package com.reajason.javaweb.memshell.server.glassfish;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * readSelector 与 closeConnection 会在不同线程并发执行。selector 必须在 register
 * 之前发布、并在 register 之后复查 closed，否则关闭线程读到 null 时新建的
 * Selector（epoll fd + wakeup pipe）会永久泄漏。
 */
class GlassFishRawWsHelperSelectorTest {

    @Test
    void selectorCreatedAfterCloseIsReclaimed() throws Exception {
        GlassFishRawWsHelper helper = new GlassFishRawWsHelper();
        SocketChannel channel = SocketChannel.open();
        try {
            setField(helper, "channel", channel);
            setField(helper, "closed", Boolean.TRUE);

            Method readSelector = GlassFishRawWsHelper.class.getDeclaredMethod("readSelector");
            readSelector.setAccessible(true);

            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                    () -> readSelector.invoke(helper));
            assertTrue(thrown.getCause() instanceof IOException);
            assertNull(readSelectorField(helper));
        } finally {
            channel.close();
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object readSelectorField(GlassFishRawWsHelper helper) throws Exception {
        Field field = GlassFishRawWsHelper.class.getDeclaredField("readSelector");
        field.setAccessible(true);
        return (Selector) field.get(helper);
    }
}
