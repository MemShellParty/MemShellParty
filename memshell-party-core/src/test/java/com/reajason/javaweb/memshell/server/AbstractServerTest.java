package com.reajason.javaweb.memshell.server;

import com.reajason.javaweb.Server;
import com.reajason.javaweb.memshell.ShellType;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自定义 shellType 不一定符合采样器的 PascalCase 约定，生成类名时要先规范化。
 *
 * @author ReaJason
 */
class AbstractServerTest {

    @Test
    void sanitizesCustomShellTypes() {
        assertEquals("MyFilter", AbstractServer.toClassNameSuffix("my_filter"));
        assertEquals("MyFilter", AbstractServer.toClassNameSuffix("myFilter"));
        assertEquals("MyFilter", AbstractServer.toClassNameSuffix("my filter"));
        assertEquals("Filter", AbstractServer.toClassNameSuffix("Filter"));
    }

    @Test
    void overlongSuffixFallsBackToHandler() {
        String tooLong = String.join("", java.util.Collections.nCopies(59, "A"));
        assertEquals("Handler", AbstractServer.toClassNameSuffix(tooLong));
    }

    @Test
    void generatesValidClassNameForCustomShellType() {
        String className = AbstractServer.generateShellClassName("Tomcat", "my_filter");
        assertTrue(className.matches("[a-z][a-z0-9.]*\\.[A-Z][A-Za-z0-9]*"), className);
        assertTrue(className.endsWith("MyFilter"), className);
    }

    @Test
    void tongWebShellUsesCacheExemptNamespace() {
        // TongWeb 的 LoadClassCache 不缓存 com.tongweb.* 的失败结果；shell 必须落在该
        // 命名空间，否则注入器的 loadClass 探测会把类名永久缓存为找不到
        String className = AbstractServer.generateShellClassName(Server.TongWeb, ShellType.FILTER);
        assertTrue(className.startsWith("com.tongweb."), className);
    }

    @Test
    void hooksDefaultToProviderLoaderAndJava6() {
        AbstractServer server = new AbstractServer() {
            @Override
            public InjectorMapping getShellInjectorMapping() {
                return InjectorMapping.builder().build();
            }
        };

        assertSame(server.getClass().getClassLoader(), server.getInjectorTemplateClassLoader());
        assertEquals(Opcodes.V1_6, server.getMinimumTargetJreVersion());
        assertEquals(AbstractServer.getWebPackageName("Blade"), server.getShellPackageName("Blade"));
        assertTrue(server.getShellClassName(Server.Tomcat, ShellType.FILTER)
                .startsWith(AbstractServer.getWebPackageName(Server.Tomcat) + "."));
    }

    @Test
    void pluginCanChooseShellPackage() {
        AbstractServer server = new AbstractServer() {
            @Override
            public InjectorMapping getShellInjectorMapping() {
                return InjectorMapping.builder().build();
            }

            @Override
            public String getShellPackageName(String serverName) {
                return "com.helloworld.web.handlers";
            }
        };

        assertTrue(server.getShellClassName("Blade", ShellType.FILTER)
                .startsWith("com.helloworld.web.handlers."));
    }

    @Test
    void supportedShellTypesRequireExistingInjectorTemplate() {
        AbstractServer server = new AbstractServer() {
            @Override
            public InjectorMapping getShellInjectorMapping() {
                return InjectorMapping.builder()
                        .addInjector("Present", AbstractServerTest.class.getName())
                        .addInjector("Absent", "does.not.ExistInjector")
                        .build();
            }
        };
        server.addToolMapping("Tool", ToolMapping.builder()
                .addShellClass("Present", "shell.Present")
                .addShellClass("Absent", "shell.Absent")
                .build());

        assertEquals(Collections.singleton("Present"), server.getSupportedShellTypes("Tool"));
    }
}
