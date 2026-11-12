package com.reajason.javaweb.memshell.generator;

import com.reajason.javaweb.GenerationException;
import com.reajason.javaweb.ShellGenerator;
import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.MemShellRegistry;
import com.reajason.javaweb.memshell.server.AbstractServer;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.pool.TypePool;
import com.reajason.javaweb.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ReaJason
 * @since 2025/5/27
 */
public abstract class ByteBuddyShellGenerator<T extends ShellToolConfig> implements ShellGenerator {
    protected final ShellConfig shellConfig;
    protected final T shellToolConfig;

    protected ByteBuddyShellGenerator(ShellConfig shellConfig, T shellToolConfig) {
        this.shellConfig = shellConfig;
        this.shellToolConfig = shellToolConfig;
    }

    protected abstract DynamicType.Builder<?> getBuilder();

    protected int getTargetJreVersion() {
        return shellConfig.getTargetJreVersion();
    }

    protected byte[] postProcessBytes(byte[] classBytes) {
        return classBytes;
    }

    /**
     * Redefine the mapped shell template without loading it into the JVM: the template
     * class file is read as a resource from the tool module's class loader, so template
     * supertypes (servlet, spring APIs) never need to be on the consumer's classpath.
     * <p>
     * The config class and the templates may come from different loaders (an SPI tool loaded by
     * a plugin loader), so the locator falls back like {@code InjectorGenerator} does.
     */
    protected DynamicType.Builder<?> redefineTemplate() {
        String templateClassName = shellToolConfig.getShellTemplateClassName();
        if (StringUtils.isBlank(templateClassName)) {
            throw new GenerationException("shellTemplateClassName is required, it is set by MemShellGenerator from the tool mapping");
        }
        for (ClassLoader classLoader : templateClassLoaders()) {
            ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.of(classLoader);
            try {
                TypeDescription typeDescription = TypePool.Default.of(classFileLocator).describe(templateClassName).resolve();
                shellToolConfig.setShellTypeDescription(typeDescription);
                return new ByteBuddy().redefine(typeDescription, classFileLocator);
            } catch (TypePool.Resolution.NoSuchTypeException ignored) {
                // 换下一个类加载器继续找
            }
        }
        throw new GenerationException("cannot resolve shell template class: " + templateClassName);
    }

    private List<ClassLoader> templateClassLoaders() {
        ClassLoader[] candidates = {
                shellToolConfig.getClass().getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                // A plugin server may supply the shell template for a built-in tool. The config
                // class then comes from the tool's loader and cannot see the plugin template;
                // the server names the loader that sees both its injectors and its shells.
                serverTemplateClassLoader(),
                ByteBuddyShellGenerator.class.getClassLoader()};
        List<ClassLoader> classLoaders = new ArrayList<ClassLoader>(candidates.length);
        for (ClassLoader candidate : candidates) {
            if (candidate != null && !classLoaders.contains(candidate)) {
                classLoaders.add(candidate);
            }
        }
        return classLoaders;
    }

    private ClassLoader serverTemplateClassLoader() {
        try {
            AbstractServer server = MemShellRegistry.getInstance().getServer(shellConfig.getServer());
            return server == null ? null : server.getInjectorTemplateClassLoader();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public byte[] getBytes() {
        DynamicType.Builder<?> builder = getBuilder();
        String shellClassName = shellToolConfig.getShellClassName();

        if (shellToolConfig.getShellTypeDescription() == null) {
            throw new GenerationException("shellTypeDescription could not be null, redefineTemplate() sets it from shellTemplateClassName.");
        }

        builder = ProcessorRegistry.applyBuilderProcessors(builder, shellConfig, shellToolConfig)
                .name(shellClassName)
                .visit(new TargetJreVersionVisitorWrapper(getTargetJreVersion()));

        try (DynamicType.Unloaded<?> unloaded = builder.make()) {
            byte[] bytes = postProcessBytes(unloaded.getBytes());
            return ProcessorRegistry.applyByteProcessors(bytes, shellConfig, shellToolConfig);
        }
    }
}
