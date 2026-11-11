package com.reajason.javaweb.memshell.server.springwebflux;

import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.server.InjectorMapping;
import net.bytebuddy.jar.asm.Opcodes;


import static com.reajason.javaweb.memshell.ShellType.*;

/**
 * @author ReaJason
 * @since 2024/12/24
 */
public class SpringWebFlux extends AbstractServer {

    /**
     * 模板使用 lambda/invokedynamic，Java 6 类文件表达不了。
     */
    @Override
    public int getMinimumTargetJreVersion() {
        return Opcodes.V1_8;
    }

    @Override
    public InjectorMapping getShellInjectorMapping() {
        return InjectorMapping.builder()
                .addInjector(SPRING_WEBFLUX_WEB_FILTER, "com.reajason.javaweb.memshell.injector.springwebflux.SpringWebFluxWebFilterInjector")
                .addInjector(NETTY_HANDLER, "com.reajason.javaweb.memshell.injector.springwebflux.SpringWebFluxNettyHandlerInjector")
                .addInjector(SPRING_WEBFLUX_HANDLER_METHOD, "com.reajason.javaweb.memshell.injector.springwebflux.SpringWebFluxHandlerMethodInjector")
                .addInjector(SPRING_WEBFLUX_HANDLER_FUNCTION, "com.reajason.javaweb.memshell.injector.springwebflux.SpringWebFluxHandlerFunctionInjector")
                .build();
    }
}
