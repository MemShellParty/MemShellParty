package com.reajason.javaweb.memshell;

import com.reajason.javaweb.Server;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.server.AbstractServer;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * server 可以声明自己的最低目标 JRE；生成入口必须把调用方给的低版本抬到这条线，
 * 否则模板里的 lambda/invokedynamic 会写出非法 class 文件。
 *
 * @author ReaJason
 */
class ServerHookGenerationTest {

    @Test
    void raisesTargetJreToServerMinimum() {
        MemShellGenerator generator = new MemShellGenerator();
        AbstractServer server = generator.getServer(Server.SpringWebFlux);
        String shellTool = server.getSupportedShellTools().iterator().next();
        String shellType = server.getSupportedShellTypes(shellTool).iterator().next();

        ShellConfig shellConfig = ShellConfig.builder()
                .server(Server.SpringWebFlux)
                .shellTool(shellTool)
                .shellType(shellType)
                .targetJreVersion(Opcodes.V1_6)
                .build();

        MemShellResult result = generator.generate(shellConfig,
                InjectorConfig.builder().urlPattern("/*").build(), Collections.emptyMap());

        assertEquals(Opcodes.V1_8, classVersion(result.getShellBytes()));
        assertEquals(Opcodes.V1_8, classVersion(result.getInjectorBytes()));
        assertEquals(Opcodes.V1_8, shellConfig.getTargetJreVersion());
    }

    /**
     * Web 表单默认开启 {@code staticInitialize}，生成前会检查模板无参构造；内置模板必须都能过这一关。
     */
    @Test
    void staticInitializeGenerationAcceptsBuiltInTemplate() {
        MemShellGenerator generator = new MemShellGenerator();
        AbstractServer server = generator.getServer(Server.SpringWebFlux);
        String shellTool = server.getSupportedShellTools().iterator().next();
        String shellType = server.getSupportedShellTypes(shellTool).iterator().next();

        MemShellResult result = generator.generate(ShellConfig.builder()
                        .server(Server.SpringWebFlux)
                        .shellTool(shellTool)
                        .shellType(shellType)
                        .build(),
                InjectorConfig.builder().urlPattern("/*").staticInitialize(true).build(),
                Collections.emptyMap());

        assertTrue(result.getInjectorBytes().length > 0);
    }

    private static int classVersion(byte[] bytes) {
        return new ClassReader(bytes).readShort(6);
    }
}
