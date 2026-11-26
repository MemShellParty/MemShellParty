package com.reajason.javaweb.integration;

import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.ShellTool;
import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.packer.Packers;
import com.reajason.javaweb.utils.tuple.Pair;
import net.bytebuddy.jar.asm.Opcodes;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RawWebSocket Proxy 的 CONNECT、双向流和心跳验收；不能用 HTTP Proxy 的 push/pull 断言代替。
 */
public final class RawWebSocketProxyNginxAssertion {
    private static final String PACKER = Packers.DefaultBase64;
    private static final byte[] ESTABLISHED = "HTTP/1.1 200 Connection Established\r\n\r\n"
            .getBytes(StandardCharsets.ISO_8859_1);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private RawWebSocketProxyNginxAssertion() {
    }

    public static ComposeContainer createCompose(String composeFile) {
        return new ComposeContainer(new File("docker-compose/rawws-proxy/" + composeFile))
                .withExposedService("app", 8080, Wait.forHttp("/app/")
                        .withStartupTimeout(Duration.ofMinutes(2)))
                .withExposedService("nginx1", 80, Wait.forHttp("/app/")
                        .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public static void inject(ComposeContainer compose, String server, String serverVersion) {
        String appUrl = baseUrl(compose, "app", 8080);
        Pair<String, String> urls = ShellAssertion.getUrls(
                appUrl, ShellType.RAW_WEBSOCKET, ShellTool.Proxy, PACKER);
        ShellToolConfig config = ShellAssertion.getShellToolConfig(
                ShellType.RAW_WEBSOCKET, ShellTool.Proxy, PACKER);
        MemShellResult result = ShellAssertion.generate(urls.getRight(), server, serverVersion,
                ShellType.RAW_WEBSOCKET, ShellTool.Proxy, Opcodes.V1_8, config, PACKER);
        ShellAssertion.packerResultAndInject(result, appUrl, ShellTool.Proxy,
                ShellType.RAW_WEBSOCKET, PACKER, null);
    }

    public static void assertProxy(ComposeContainer compose, String entryService) throws Exception {
        int entryPort = "app".equals(entryService) ? 8080 : 80;
        String entrypoint = ShellAssertion.getUrls(baseUrl(compose, entryService, entryPort),
                ShellType.RAW_WEBSOCKET, ShellTool.Proxy, PACKER).getLeft();
        long started = System.nanoTime();
        ProxyClient client = new ProxyClient(URI.create(entrypoint));
        try {
            client.open();
            client.send("CONNECT echo:9000 HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            client.assertNext(ESTABLISHED);

            byte[] firstPayload = payload(32 * 1024 + 17, 11);
            client.send(firstPayload);
            client.assertNext(firstPayload);

            // Nginx 的 proxy_read_timeout 为 3s；心跳跨越 5s 后仍需保持同一隧道。
            for (int i = 0; i < 5; i++) {
                client.sendPing();
                assertNotNull(client.pongs.poll(3, TimeUnit.SECONDS),
                        "Missing WebSocket PONG via " + entryService + client.status());
                Thread.sleep(1000);
            }
            assertTrue(client.isOpen(), "WebSocket closed during heartbeat" + client.status());

            byte[] secondPayload = payload(128 * 1024 + 43, 29);
            client.send(secondPayload);
            client.assertNext(secondPayload);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            System.out.println("RawWebSocket Proxy " + entryService + " PASS: "
                    + firstPayload.length + " + " + secondPayload.length
                    + " bytes echoed, 5 PONGs, " + elapsedMillis + " ms");
        } catch (Exception | Error failure) {
            compose.getContainerByServiceName("nginx1").ifPresent(container ->
                    System.out.println("nginx1 logs:\n" + container.getLogs()));
            throw failure;
        } finally {
            client.closeBlocking();
        }
    }

    private static String baseUrl(ComposeContainer compose, String service, int port) {
        return "http://" + compose.getServiceHost(service, port) + ":"
                + compose.getServicePort(service, port) + "/app";
    }

    private static byte[] payload(int size, int salt) {
        byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31 + salt);
        }
        return data;
    }

    private static final class ProxyClient extends WebSocketClient {
        private final CountDownLatch openLatch = new CountDownLatch(1);
        private final BlockingQueue<byte[]> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<byte[]> pongs = new LinkedBlockingQueue<>();
        private volatile Exception failure;
        private volatile int closeCode = -1;
        private ByteBuffer current;

        private ProxyClient(URI uri) {
            super(uri);
            setConnectionLostTimeout(0);
        }

        private void open() throws InterruptedException {
            connect();
            assertTrue(openLatch.await(10, TimeUnit.SECONDS),
                    "WebSocket handshake timeout" + status());
            assertTrue(isOpen(), "WebSocket handshake failed" + status());
        }

        private void assertNext(byte[] expected) throws InterruptedException {
            byte[] actual = readExactly(expected.length);
            assertEquals(expected.length, actual.length, "Truncated proxy stream" + status());
            assertArrayEquals(expected, actual, "Proxy payload differs" + status());
        }

        private byte[] readExactly(int length) throws InterruptedException {
            byte[] result = new byte[length];
            int offset = 0;
            long deadline = System.nanoTime() + READ_TIMEOUT.toNanos();
            while (offset < length) {
                if (current == null || !current.hasRemaining()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }
                    byte[] message = messages.poll(remaining, TimeUnit.NANOSECONDS);
                    if (message == null) {
                        break;
                    }
                    current = ByteBuffer.wrap(message);
                }
                int count = Math.min(current.remaining(), length - offset);
                current.get(result, offset, count);
                offset += count;
            }
            if (offset < length) {
                byte[] partial = new byte[offset];
                System.arraycopy(result, 0, partial, 0, offset);
                return partial;
            }
            return result;
        }

        private String status() {
            return " (open=" + isOpen() + ", closeCode=" + closeCode + ", error=" + failure + ")";
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            openLatch.countDown();
        }

        @Override
        public void onMessage(String message) {
            messages.add(message.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void onMessage(ByteBuffer message) {
            byte[] data = new byte[message.remaining()];
            message.get(data);
            messages.add(data);
        }

        @Override
        public void onWebsocketPong(WebSocket connection, Framedata frame) {
            ByteBuffer payload = frame.getPayloadData();
            byte[] data = new byte[payload.remaining()];
            payload.get(data);
            pongs.add(data);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closeCode = code;
            openLatch.countDown();
        }

        @Override
        public void onError(Exception error) {
            failure = error;
            openLatch.countDown();
        }
    }
}
