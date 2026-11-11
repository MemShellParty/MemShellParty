package com.reajason.javaweb.memshell;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次插件目录校验的结果。校验只读：不注册 provider，也不改写 {@link MemShellRegistry} /
 * {@link PackerRegistry} 的状态，因此可以在宿主启动前或构建期单独运行。
 *
 * @author ReaJason
 * @since 3.2.0
 */
public final class PluginValidationReport {

    private final Path directory;
    private final List<Path> pluginJars;
    private final List<String> errors;
    private final List<String> warnings;

    PluginValidationReport(Path directory, List<Path> pluginJars,
                           List<String> errors, List<String> warnings) {
        this.directory = directory;
        this.pluginJars = Collections.unmodifiableList(new ArrayList<>(pluginJars));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public Path getDirectory() {
        return directory;
    }

    public List<Path> getPluginJars() {
        return pluginJars;
    }

    /**
     * 会让生成在运行期失败的问题：模板缺失、契约方法缺失、provider 实例化失败等。
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * 不阻止插件加载、但会在特定配置下失败的问题（例如模板缺少无参构造，开启
     * {@code staticInitialize} 时才会出错）。
     */
    public List<String> getWarnings() {
        return warnings;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
