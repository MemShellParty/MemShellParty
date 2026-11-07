package com.reajason.javaweb.memshell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * boot 与桌面发行版共用的启动期插件入口。只装载三类核心 SPI，不接管宿主的其他 SPI。
 */
public final class PluginBootstrap {
    public static final String DIRECTORY_PROPERTY = "memshell.plugins.dir";
    public static final String DIRECTORY_ENV = "MEMSHELL_PLUGINS_DIR";
    /**
     * 插件 jar 内可选的元信息描述符。字段全部可选，缺失时回退到 MANIFEST 与 jar 文件名。
     */
    public static final String DESCRIPTOR_RESOURCE = "META-INF/memshell-party-plugin.properties";

    private static final String[] SPI_SERVICES = {
            "com.reajason.javaweb.memshell.spi.ServerProvider",
            "com.reajason.javaweb.memshell.spi.ShellToolProvider",
            "com.reajason.javaweb.packer.Packer"
    };

    private static final String[] SPI_SERVICE_NAMES = {
            "ServerProvider",
            "ShellToolProvider",
            "Packer"
    };

    private static volatile PluginLoadResult lastResult;

    private PluginBootstrap() {
    }

    /**
     * 必须在首次访问两个核心注册表之前调用。新增、更新或删除插件均需重启。
     */
    public static PluginLoadResult load() {
        String configured = System.getProperty(DIRECTORY_PROPERTY);
        if (isBlank(configured)) {
            configured = System.getenv(DIRECTORY_ENV);
        }
        Path baseDirectory = launchBaseDirectory(
                System.getProperty("java.class.path"),
                System.getProperty("sun.java.command"),
                System.getProperty("user.dir"));
        return load(resolvePluginDirectory(configured, baseDirectory), Thread.currentThread().getContextClassLoader());
    }

    static PluginLoadResult load(Path directory, ClassLoader parentLoader) {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        List<String> warnings = new ArrayList<>();
        List<Path> scannedJars = scanPluginJars(normalizedDirectory, warnings);
        List<URL> urls = new ArrayList<>(scannedJars.size());
        List<Path> loadedJars = new ArrayList<>(scannedJars.size());
        List<PluginDescriptor> plugins = new ArrayList<>(scannedJars.size());
        for (Path jar : scannedJars) {
            PluginJar pluginJar;
            try {
                pluginJar = readPluginJar(jar);
            } catch (IOException | RuntimeException e) {
                String message = SpiLoader.message(e);
                warnings.add("跳过损坏插件 jar: " + jar + ": " + message);
                plugins.add(unreadableDescriptor(jar, message));
                continue;
            }
            if (pluginJar.descriptorWarning != null) {
                // 元信息损坏不应该阻止插件本身加载，回退到 manifest / 文件名继续。
                warnings.add("读取插件描述失败: " + jar + ": " + pluginJar.descriptorWarning);
            }
            if (!pluginJar.isPlugin()) {
                continue;
            }
            if (pluginJar.validationError != null) {
                warnings.add("跳过损坏插件 jar: " + jar + ": " + pluginJar.validationError);
                plugins.add(pluginJar.toDescriptor(false, pluginJar.validationError));
                continue;
            }
            try {
                urls.add(jar.toUri().toURL());
                loadedJars.add(jar);
                plugins.add(pluginJar.toDescriptor(true, null));
            } catch (MalformedURLException e) {
                String message = SpiLoader.message(e);
                warnings.add("插件路径无效: " + jar + ": " + message);
                plugins.add(pluginJar.toDescriptor(false, message));
            }
        }

        ClassLoader effectiveParent = parentLoader != null ? parentLoader : PluginBootstrap.class.getClassLoader();
        ClassLoader pluginLoader = urls.isEmpty() ? effectiveParent
                : new PluginClassLoader(urls.toArray(new URL[0]), effectiveParent);
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(pluginLoader);
            try {
                MemShellRegistry.getInstance().init();
            } catch (RuntimeException | LinkageError e) {
                warnings.add("初始化 server/shellTool 插件失败: " + SpiLoader.message(e));
            }
            try {
                PackerRegistry.getInstance().init();
            } catch (RuntimeException | LinkageError e) {
                warnings.add("初始化 packer 插件失败: " + SpiLoader.message(e));
            }
        } finally {
            // 预热的 provider 会保留自己的 defining loader；恢复 TCCL 可避免加载 GuiExtension、
            // Spring 自动配置及插件中的其他 SPI。
            thread.setContextClassLoader(originalLoader);
        }
        warnings.addAll(MemShellRegistry.getInstance().getLoadErrors());
        warnings.addAll(PackerRegistry.getInstance().getLoadErrors());
        PluginLoadResult result = new PluginLoadResult(normalizedDirectory, loadedJars, plugins, warnings);
        lastResult = result;
        return result;
    }

    /**
     * 最近一次 {@link #load()} 的结果，供 boot 的 {@code /api/plugins} 与桌面端「关于」只读展示。
     * 未加载过时返回 {@code null}；插件生命周期仍以重启为准，不存在热更新语义。
     */
    public static PluginLoadResult getLastResult() {
        return lastResult;
    }

    static Path resolvePluginDirectory(String configured, Path baseDirectory) {
        Path normalizedBase = baseDirectory.toAbsolutePath().normalize();
        if (isBlank(configured)) {
            return normalizedBase.resolve("plugins");
        }
        Path configuredPath = Paths.get(configured.trim());
        return configuredPath.isAbsolute() ? configuredPath.normalize()
                : normalizedBase.resolve(configuredPath).normalize();
    }

    static Path launchBaseDirectory(String classPath, String javaCommand, String userDir) {
        Path fallback = Paths.get(isBlank(userDir) ? "." : userDir).toAbsolutePath().normalize();
        if (isBlank(classPath) || classPath.indexOf(File.pathSeparatorChar) >= 0
                || !commandReferencesClassPath(javaCommand, classPath)) {
            return fallback;
        }
        try {
            Path candidate = Paths.get(classPath).toAbsolutePath().normalize();
            String fileName = candidate.getFileName() == null ? "" : candidate.getFileName().toString();
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".jar") && Files.isRegularFile(candidate)) {
                Path parent = candidate.getParent();
                return parent == null ? fallback : parent;
            }
        } catch (RuntimeException ignored) {
            // 非标准启动场景回退到工作目录，不阻止宿主启动。
        }
        return fallback;
    }

    static List<Path> scanPluginJars(Path directory, List<String> warnings) {
        List<Path> jars = new ArrayList<>();
        try {
            if (!Files.exists(directory)) {
                return Collections.emptyList();
            }
            if (!Files.isDirectory(directory)) {
                warnings.add("插件目录不是目录: " + directory);
                return Collections.emptyList();
            }
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
                for (Path entry : entries) {
                    String fileName = entry.getFileName().toString();
                    if (fileName.toLowerCase(Locale.ROOT).endsWith(".jar") && Files.isRegularFile(entry)) {
                        jars.add(entry.toAbsolutePath().normalize());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            warnings.add("扫描插件目录失败: " + directory + ": " + SpiLoader.message(e));
            return Collections.emptyList();
        }
        jars.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return jars;
    }

    private static PluginJar readPluginJar(Path jar) throws IOException {
        String jarFileName = jar.getFileName() == null ? "" : jar.getFileName().toString();
        Properties descriptor = new Properties();
        boolean hasDescriptor = false;
        String descriptorWarning = null;
        List<String> spiServices = new ArrayList<>();
        String validationError = null;
        String implementationTitle = null;
        String implementationVersion = null;
        String implementationVendor = null;
        String implementationUrl = null;
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry descriptorEntry = jarFile.getJarEntry(DESCRIPTOR_RESOURCE);
            if (descriptorEntry != null) {
                hasDescriptor = true;
                try (Reader reader = new InputStreamReader(
                        jarFile.getInputStream(descriptorEntry), StandardCharsets.UTF_8)) {
                    descriptor.load(reader);
                } catch (IOException | RuntimeException e) {
                    // 元信息文件本身坏了只影响展示，下面仍然回退到 manifest / 文件名。
                    descriptorWarning = SpiLoader.message(e);
                }
            }
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                implementationTitle = attributes.getValue("Implementation-Title");
                implementationVersion = attributes.getValue("Implementation-Version");
                implementationVendor = attributes.getValue("Implementation-Vendor");
                implementationUrl = attributes.getValue("Implementation-URL");
            }
            for (int i = 0; i < SPI_SERVICES.length; i++) {
                JarEntry entry = jarFile.getJarEntry("META-INF/services/" + SPI_SERVICES[i]);
                if (entry == null) {
                    continue;
                }
                spiServices.add(SPI_SERVICE_NAMES[i]);
                if (validationError == null) {
                    validationError = validateServiceDescriptor(jarFile, entry);
                }
            }
        }
        return new PluginJar(jarFileName, descriptor, hasDescriptor, descriptorWarning, spiServices,
                implementationTitle, implementationVersion, implementationVendor, implementationUrl,
                validationError);
    }

    private static String validateServiceDescriptor(JarFile jarFile, JarEntry entry) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                jarFile.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                String className = (comment >= 0 ? line.substring(0, comment) : line).trim();
                if (!className.isEmpty() && !isJavaClassName(className)) {
                    return "无效 SPI 实现类名: " + className;
                }
            }
        }
        return null;
    }

    private static PluginDescriptor unreadableDescriptor(Path jar, String message) {
        String fileName = jar.getFileName() == null ? "" : jar.getFileName().toString();
        return new PluginDescriptor(fileName, defaultPluginName(fileName), null, null, null, null,
                null, null, Collections.<String>emptyList(), false, message);
    }

    private static String defaultPluginName(String jarFileName) {
        if (jarFileName == null || jarFileName.isEmpty()) {
            return "";
        }
        return jarFileName.toLowerCase(Locale.ROOT).endsWith(".jar")
                ? jarFileName.substring(0, jarFileName.length() - 4)
                : jarFileName;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    private static final class PluginJar {
        private final String jarFileName;
        private final Properties descriptor;
        private final boolean hasDescriptor;
        private final String descriptorWarning;
        private final List<String> spiServices;
        private final String implementationTitle;
        private final String implementationVersion;
        private final String implementationVendor;
        private final String implementationUrl;
        private final String validationError;

        private PluginJar(String jarFileName, Properties descriptor, boolean hasDescriptor,
                          String descriptorWarning, List<String> spiServices,
                          String implementationTitle, String implementationVersion,
                          String implementationVendor, String implementationUrl,
                          String validationError) {
            this.jarFileName = jarFileName;
            this.descriptor = descriptor;
            this.hasDescriptor = hasDescriptor;
            this.descriptorWarning = descriptorWarning;
            this.spiServices = spiServices;
            this.implementationTitle = implementationTitle;
            this.implementationVersion = implementationVersion;
            this.implementationVendor = implementationVendor;
            this.implementationUrl = implementationUrl;
            this.validationError = validationError;
        }

        private boolean isPlugin() {
            return hasDescriptor || !spiServices.isEmpty();
        }

        private PluginDescriptor toDescriptor(boolean loaded, String loadError) {
            String name = firstNonBlank(descriptor.getProperty("name"), implementationTitle,
                    defaultPluginName(jarFileName));
            String version = firstNonBlank(descriptor.getProperty("version"), implementationVersion);
            String author = firstNonBlank(descriptor.getProperty("author"), implementationVendor);
            String homepage = firstNonBlank(descriptor.getProperty("homepage"),
                    descriptor.getProperty("releaseUrl"), descriptor.getProperty("url"),
                    implementationUrl);
            String description = firstNonBlank(descriptor.getProperty("description"));
            String license = firstNonBlank(descriptor.getProperty("license"));
            String source = firstNonBlank(descriptor.getProperty("source"),
                    descriptor.getProperty("sourceUrl"));
            return new PluginDescriptor(jarFileName, name, version, author, description, homepage,
                    license, source, spiServices, loaded, loadError);
        }
    }

    private static boolean isJavaClassName(String className) {
        String[] segments = className.split("\\.", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) {
                return false;
            }
            for (int i = 1; i < segment.length(); i++) {
                if (!Character.isJavaIdentifierPart(segment.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean commandReferencesClassPath(String javaCommand, String classPath) {
        if (javaCommand == null) {
            return false;
        }
        String command = javaCommand.trim();
        return command.equals(classPath) || command.startsWith(classPath + " ")
                || command.startsWith(classPath + "\t") || command.equals('"' + classPath + '"')
                || command.startsWith('"' + classPath + "\" ") || command.startsWith('"' + classPath + "\"\t");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
