package com.reajason.javaweb.boot.controller;

import com.reajason.javaweb.memshell.PluginBootstrap;
import com.reajason.javaweb.memshell.PluginDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /api/plugins} 只读取启动期的插件扫描结果，不重新扫目录。
 */
class PluginControllerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearPluginDirectory() {
        System.clearProperty(PluginBootstrap.DIRECTORY_PROPERTY);
    }

    @Test
    void returnsStartupPluginDescriptors() throws Exception {
        Path jar = tempDir.resolve("demo-plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("META-INF/memshell-party-plugin.properties"));
            output.write(("name=Demo Plugin\n"
                    + "version=1.2.3\n"
                    + "author=Alice\n"
                    + "description=Demo description\n"
                    + "homepage=https://example.com\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        System.setProperty(PluginBootstrap.DIRECTORY_PROPERTY, tempDir.toString());
        PluginBootstrap.load();

        List<PluginDescriptor> plugins = new PluginController().plugins();

        assertEquals(1, plugins.size());
        assertEquals("demo-plugin.jar", plugins.get(0).getJarFileName());
        assertEquals("Demo Plugin", plugins.get(0).getName());
        assertEquals("1.2.3", plugins.get(0).getVersion());
        assertEquals("Alice", plugins.get(0).getAuthor());
        assertEquals("Demo description", plugins.get(0).getDescription());
        assertEquals("https://example.com", plugins.get(0).getHomepage());
        assertTrue(plugins.get(0).isLoaded());
    }
}
