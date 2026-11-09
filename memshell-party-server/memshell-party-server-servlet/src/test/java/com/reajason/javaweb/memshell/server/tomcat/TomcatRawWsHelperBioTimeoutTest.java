package com.reajason.javaweb.memshell.server.tomcat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BIO 连接器的 socket 带 SO_TIMEOUT（Tomcat 5/6/7/8.0 默认 20s），空闲读到点是
 * SocketTimeoutException 而不是数据。它必须按"本轮无数据"处理，不能把会话结束掉。
 */
class TomcatRawWsHelperBioTimeoutTest {

    private static final int MODE_BIO = 1;

    @Test
    void bioReadTimeoutIsPolledInsteadOfEndingSession() throws Exception {
        TomcatRawWsHelper helper = new TomcatRawWsHelper();
        setField(helper, "wireMode", MODE_BIO);
        setField(helper, "input", new TimeoutThenByteInputStream());

        Method readWireByte = TomcatRawWsHelper.class.getDeclaredMethod("readWireByte");
        readWireByte.setAccessible(true);

        // 第一次 read 抛 SocketTimeoutException；修复后应继续轮询并读到 0x41。
        assertEquals(0x41, ((Integer) readWireByte.invoke(helper)).intValue());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TimeoutThenByteInputStream extends InputStream {

        private int calls;

        @Override
        public int read() throws IOException {
            return read(new byte[1], 0, 1) == 1 ? 0x41 : -1;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (calls++ == 0) {
                throw new SocketTimeoutException("Read timed out");
            }
            if (calls == 2) {
                buffer[offset] = 0x41;
                return 1;
            }
            return -1;
        }
    }
}
