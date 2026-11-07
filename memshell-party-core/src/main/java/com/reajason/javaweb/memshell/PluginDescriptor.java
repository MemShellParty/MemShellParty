package com.reajason.javaweb.memshell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 启动期扫描到的插件 jar 的只读元信息。
 *
 * <p>元信息来源优先级：jar 内 {@value PluginBootstrap#DESCRIPTOR_RESOURCE}、
 * {@code MANIFEST.MF} 的标准 {@code Implementation-*} 属性、jar 文件名。没有描述符的旧插件也会出现在
 * {@link PluginLoadResult#getPlugins()} 里，不会被静默丢弃。
 *
 * <p>这些字段由插件自行声明，宿主不做签名或可信校验，只能用于展示，不能作为权限依据。
 *
 * @author ReaJason
 * @since 3.1.0
 */
public final class PluginDescriptor {
    private final String jarFileName;
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String homepage;
    private final String license;
    private final String source;
    private final List<String> spiServices;
    private final boolean loaded;
    private final String loadError;

    PluginDescriptor(String jarFileName, String name, String version, String author,
                     String description, String homepage, String license, String source,
                     List<String> spiServices, boolean loaded, String loadError) {
        this.jarFileName = jarFileName == null ? "" : jarFileName;
        this.name = name == null || name.trim().isEmpty() ? this.jarFileName : name.trim();
        this.version = normalize(version);
        this.author = normalize(author);
        this.description = normalize(description);
        this.homepage = normalize(homepage);
        this.license = normalize(license);
        this.source = normalize(source);
        this.spiServices = Collections.unmodifiableList(new ArrayList<String>(
                spiServices == null ? Collections.<String>emptyList() : spiServices));
        this.loaded = loaded;
        this.loadError = normalize(loadError);
    }

    /**
     * 插件 jar 的文件名（不含目录），列表展示与去重都用它。
     */
    public String getJarFileName() {
        return jarFileName;
    }

    /**
     * 展示名。未声明时回退到 jar 文件名，因此不会为空。
     */
    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 插件发布地址 / 主页；只用于展示，宿主不会自动请求它。
     */
    public String getHomepage() {
        return homepage;
    }

    public String getLicense() {
        return license;
    }

    /**
     * 源码地址（可选）。
     */
    public String getSource() {
        return source;
    }

    /**
     * 该 jar 声明实现的核心 SPI 简单名，如 {@code ServerProvider}、{@code Packer}。
     */
    public List<String> getSpiServices() {
        return spiServices;
    }

    /**
     * jar 是否已加入插件类加载器。{@code false} 表示 service 描述符损坏或被跳过，
     * 但该插件仍会出现在列表里，{@link #getLoadError()} 给出原因。
     */
    public boolean isLoaded() {
        return loaded;
    }

    public String getLoadError() {
        return loadError;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
