package com.reajason.javaweb.memshell;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.reajason.javaweb.GenerationException;
import com.reajason.javaweb.InvalidInputException;
import com.reajason.javaweb.memshell.config.ConfigField;
import com.reajason.javaweb.memshell.config.RemoteOptions;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.spi.ShellToolProvider;
import com.reajason.javaweb.utils.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具配置的统一出入口：按 {@link ConfigField} 注解读出每个 shell tool 的可配置参数，
 * 并把原始参数绑定回工具 config，使调用方（REST、MCP、桌面端）不必依赖具体工具模块，
 * SPI 新增的工具也不用改任何一行代码。
 * <p>
 * 参数名就是 config 的字段名（{@code pass} / {@code key} / {@code paramName}），不做工具名前缀；
 * 留空的值一律跳过，让 builder 默认值（随机 pass/key 等）生效；写了不存在的参数名则直接报错，
 * 避免客户端的拼写错误被静默吞掉。
 *
 * @author ReaJason
 * @since 3.0.0
 */
public final class ToolConfigFactory {

    /**
     * 生成期参数：不属于工具参数（不出现在配置目录里），但要能被调用方指定，
     * 所以绑定时的白名单 = 注解字段 + 这里。
     */
    private static final Set<String> GENERATION_PARAMS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Collections.singletonList("shellClassName")));

    private ToolConfigFactory() {
    }

    /**
     * Build the tool config for {@code toolName} from raw parameters. String and enum values
     * retain their trim/blank semantics; map values are bound as defensive copies.
     * Package-private: callers outside core use {@link MemShellGenerator#generate},
     * which forwards to this binder.
     *
     * @throws GenerationException when the tool is not registered, or a parameter is unknown / unbindable
     */
    static ShellToolConfig fromParams(String toolName, Map<String, ?> params) {
        ShellToolProvider provider = MemShellRegistry.getInstance().getToolProvider(toolName);
        if (provider == null) {
            throw new InvalidInputException("Unsupported shell tool: " + toolName);
        }
        Class<? extends ShellToolConfig> configClass = provider.getConfigClass();
        ShellToolConfig config = newConfig(configClass);
        if (params == null) {
            return config;
        }
        Set<String> supported = supportedParams(configClass);
        for (Map.Entry<String, ?> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                // null = 用默认值
                continue;
            }
            String name = entry.getKey();
            if (value instanceof String && StringUtils.isBlank((String) value)) {
                Field field = supported.contains(name) ? findField(configClass, name) : null;
                if (field == null || !Map.class.isAssignableFrom(field.getType())) {
                    // String / enum 留空 = 用默认值；未知字段的留空值也维持原来的忽略行为
                    continue;
                }
            }
            if (!supported.contains(name)) {
                throw new InvalidInputException("Unsupported param for tool " + toolName + ": " + name
                        + ", supported params: " + String.join(", ", supported));
            }
            setField(configClass, config, name, value);
        }
        return config;
    }

    /**
     * Introspect every registered tool's configurable parameters, in
     * {@link MemShellRegistry#getSupportedTools()} (i.e. {@code @ShellToolMeta(order)}) order.
     */
    public static List<ToolConfigDescriptor> describeAll() {
        List<ToolConfigDescriptor> descriptors = new ArrayList<>();
        for (String toolName : MemShellRegistry.getInstance().getSupportedTools()) {
            descriptors.add(new ToolConfigDescriptor(toolName, describeParams(toolName)));
        }
        return descriptors;
    }

    /**
     * Introspect one tool's configurable parameters from its config class: fields marked with
     * {@link ConfigField}, in declaration order (subclass first), excluded the generation
     * config's own fields. String fields are text inputs, enum fields get their constant names as options,
     * and Map fields are objects. {@link RemoteOptions} adds remote-select UI metadata to String and Map fields.
     */
    public static List<ConfigParam> describeParams(String toolName) {
        ShellToolProvider provider = MemShellRegistry.getInstance().getToolProvider(toolName);
        if (provider == null) {
            return Collections.emptyList();
        }
        List<ConfigParam> params = new ArrayList<>();
        for (Class<?> type = provider.getConfigClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                ConfigField annotation = field.getAnnotation(ConfigField.class);
                if (annotation != null) {
                    params.add(toParam(field, annotation));
                }
            }
        }
        return params;
    }

    /**
     * Read a parameter's default value from a freshly built config (e.g. {@code headerName}
     * defaults to {@code Referer} for NeoreGeorg). Returns null when absent.
     * <p>
     * 随机默认值（pass/key/headerValue）每次调用都不同，只适合当占位提示，不要当成稳定值缓存。
     */
    public static String defaultParamValue(String toolName, String paramName) {
        ShellToolProvider provider = MemShellRegistry.getInstance().getToolProvider(toolName);
        if (provider == null) {
            return null;
        }
        try {
            ShellToolConfig config = newConfig(provider.getConfigClass());
            String suffix = Character.toUpperCase(paramName.charAt(0)) + paramName.substring(1);
            for (String getter : new String[]{"get" + suffix, "is" + suffix}) {
                try {
                    Object value = config.getClass().getMethod(getter).invoke(config);
                    return value == null ? null : String.valueOf(value);
                } catch (NoSuchMethodException ignored) {
                    // try the next getter style
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ConfigParam toParam(Field field, ConfigField annotation) {
        Class<?> type = field.getType();
        RemoteSelectUi ui = remoteSelectUi(field);
        if (type == String.class) {
            return new ConfigParam(field.getName(), "string", new ArrayList<String>(), annotation.group(),
                    annotation.label(), annotation.placeholder(), ui);
        }
        if (type.isEnum()) {
            return new ConfigParam(field.getName(), "enum", enumNames(type), annotation.group(),
                    annotation.label(), annotation.placeholder(), ui);
        }
        if (Map.class.isAssignableFrom(type)) {
            return new ConfigParam(field.getName(), "object", new ArrayList<String>(), annotation.group(),
                    annotation.label(), annotation.placeholder(), ui);
        }
        // 静默跳过会让字段从表单里凭空消失，不如在目录接口/测试里直接炸出来
        throw new GenerationException("Unsupported @ConfigField type: " + field.getDeclaringClass().getName()
                + "." + field.getName() + " (" + type.getSimpleName()
                + "), only String, enum and Map are supported");
    }

    private static RemoteSelectUi remoteSelectUi(Field field) {
        RemoteOptions annotation = field.getAnnotation(RemoteOptions.class);
        if (annotation == null) {
            return null;
        }
        Class<?> type = field.getType();
        if (type != String.class && !Map.class.isAssignableFrom(type)) {
            throw new GenerationException("@RemoteOptions requires a String or Map field: "
                    + field.getDeclaringClass().getName() + "." + field.getName());
        }
        if (StringUtils.isBlank(annotation.urlQueryParam()) || StringUtils.isBlank(annotation.labelPath())) {
            throw new GenerationException("@RemoteOptions urlQueryParam and labelPath must not be blank: "
                    + field.getDeclaringClass().getName() + "." + field.getName());
        }
        if (type == String.class && StringUtils.isBlank(annotation.valuePath())) {
            throw new GenerationException("@RemoteOptions valuePath is required for a String field: "
                    + field.getDeclaringClass().getName() + "." + field.getName());
        }
        return new RemoteSelectUi(annotation);
    }

    private static Set<String> supportedParams(Class<? extends ShellToolConfig> configClass) {
        Set<String> supported = new LinkedHashSet<>(GENERATION_PARAMS);
        for (Class<?> type = configClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getAnnotation(ConfigField.class) != null) {
                    supported.add(field.getName());
                }
            }
        }
        return supported;
    }

    /**
     * Lombok 的 {@code *BuilderImpl} 是包私有类且默认值（含随机值）由 builder 生成，
     * 所以先 build 出一份带默认值的 config，再按字段写入调用方显式给的值。
     */
    private static ShellToolConfig newConfig(Class<? extends ShellToolConfig> configClass) {
        try {
            Object builder = configClass.getMethod("builder").invoke(null);
            Method build = builder.getClass().getMethod("build");
            build.setAccessible(true);
            return (ShellToolConfig) build.invoke(builder);
        } catch (Exception e) {
            throw new GenerationException("failed to build config for " + configClass.getName()
                    + ", " + e.getMessage(), e);
        }
    }

    private static void setField(Class<? extends ShellToolConfig> configClass, ShellToolConfig config,
                                 String name, Object value) {
        Field field = findField(configClass, name);
        if (field == null) {
            throw new InvalidInputException("Unsupported param for tool config " + configClass.getName()
                    + ": " + name);
        }
        try {
            field.setAccessible(true);
            Class<?> type = field.getType();
            if (type == String.class) {
                if (!(value instanceof String)) {
                    throw expectedValueType(name, "string", value);
                }
                field.set(config, ((String) value).trim());
            } else if (type.isEnum()) {
                if (!(value instanceof String)) {
                    throw expectedValueType(name, "string", value);
                }
                field.set(config, toEnum(type, name, ((String) value).trim()));
            } else if (Map.class.isAssignableFrom(type)) {
                if (!(value instanceof Map)) {
                    throw expectedValueType(name, "object", value);
                }
                field.set(config, copyMap(name, (Map<?, ?>) value));
            } else {
                throw new GenerationException("Unsupported param type for " + name + ": " + type.getSimpleName());
            }
        } catch (IllegalAccessException e) {
            throw new GenerationException("failed to set param " + name + ": " + e.getMessage(), e);
        }
    }

    private static InvalidInputException expectedValueType(String name, String expected, Object value) {
        return new InvalidInputException("Expected " + expected + " value for param " + name
                + ", got " + value.getClass().getSimpleName());
    }

    private static Map<String, Object> copyMap(String name, Map<?, ?> value) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new InvalidInputException("Expected string keys in object value for param " + name);
            }
            copy.put((String) entry.getKey(), entry.getValue());
        }
        return copy;
    }

    private static Field findField(Class<?> configClass, String name) {
        for (Class<?> type = configClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().equals(name) && !Modifier.isStatic(field.getModifiers())) {
                    return field;
                }
            }
        }
        return null;
    }

    private static Object toEnum(Class<?> enumType, String paramName, String value) {
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equalsIgnoreCase(value)) {
                return constant;
            }
        }
        throw new InvalidInputException("Unsupported value for param " + paramName + ": " + value
                + ", expected one of " + String.join(", ", enumNames(enumType)));
    }

    private static List<String> enumNames(Class<?> enumType) {
        List<String> names = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            names.add(((Enum<?>) constant).name());
        }
        return names;
    }

    /**
     * One shell tool and its configurable parameters, as served to REST / MCP / desktop UI.
     */
    public static class ToolConfigDescriptor {
        private final String tool;
        private final List<ConfigParam> params;

        public ToolConfigDescriptor(String tool, List<ConfigParam> params) {
            this.tool = tool;
            this.params = params;
        }

        public String getTool() {
            return tool;
        }

        public List<ConfigParam> getParams() {
            return params;
        }
    }

    /**
     * One configurable parameter of a shell tool.
     */
    public static class ConfigParam {
        private final String name;
        private final String type;
        private final List<String> options;
        private final ConfigField.Group group;
        private final String label;
        private final String placeholder;
        private final RemoteSelectUi ui;

        public ConfigParam(String name, String type, List<String> options, ConfigField.Group group,
                           String label, String placeholder) {
            this(name, type, options, group, label, placeholder, null);
        }

        public ConfigParam(String name, String type, List<String> options, ConfigField.Group group,
                           String label, String placeholder, RemoteSelectUi ui) {
            this.name = name;
            this.type = type;
            this.options = options;
            this.group = group;
            this.label = label;
            this.placeholder = placeholder;
            this.ui = ui;
        }

        public String getName() {
            return name;
        }

        /**
         * @return {@code string}, {@code enum} or {@code object}
         */
        public String getType() {
            return type;
        }

        /**
         * @return enum constant names; empty for string and object params
         */
        public List<String> getOptions() {
            return options;
        }

        public ConfigField.Group getGroup() {
            return group;
        }

        /**
         * @return 注解读出的字段显示名；空串表示让界面按内置参数名兜底
         */
        public String getLabel() {
            return label;
        }

        /**
         * @return 注解读出的占位提示；空串表示让界面按内置参数兜底
         */
        public String getPlaceholder() {
            return placeholder;
        }

        /**
         * @return remote-select metadata, or null for an ordinary input
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public RemoteSelectUi getUi() {
            return ui;
        }
    }

    /**
     * UI metadata for a {@link RemoteOptions} field.
     */
    public static class RemoteSelectUi {
        private final String kind = "remote-select";
        private final String urlQueryParam;
        private final String itemsPath;
        private final String labelPath;
        private final String valuePath;
        private final boolean required;

        private RemoteSelectUi(RemoteOptions annotation) {
            this.urlQueryParam = annotation.urlQueryParam();
            this.itemsPath = annotation.itemsPath();
            this.labelPath = annotation.labelPath();
            this.valuePath = annotation.valuePath();
            this.required = annotation.required();
        }

        public String getKind() {
            return kind;
        }

        public String getUrlQueryParam() {
            return urlQueryParam;
        }

        public String getItemsPath() {
            return itemsPath;
        }

        public String getLabelPath() {
            return labelPath;
        }

        public String getValuePath() {
            return valuePath;
        }

        public boolean isRequired() {
            return required;
        }
    }
}
