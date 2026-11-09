package com.reajason.javaweb.integration;

import com.reajason.javaweb.Server;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.ShellTool;
import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.packer.Packers;
import com.reajason.javaweb.utils.tuple.Pair;
import net.bytebuddy.jar.asm.Opcodes;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * 一次性真实容器校验：覆盖当前环境可用的全部 WsHelper 实现。
 * <p>
 * 该源码集不参与 {@code :integration-test:test}，只由 {@code wsHelperVerification} 任务执行，
 * 避免重容器校验拖慢 CI。每个 profile 用 Proxy RAW_WEBSOCKET 发起真实 CONNECT 隧道，
 * 校验目标 socket、泵线程在正常/异常/失败/并发场景下都能释放。
 *
 * @author ReaJason
 */
public class WsHelperVerificationTest {

    private static final int TARGET_PORT = 9000;
    private static final String TARGET_HOST = "echo";
    private static final long WAIT_SECONDS = 20;
    /** 同一 shell 端点上反复建连/用/关的次数，可用 -PwsCycles=N 覆盖。 */
    private static final int CYCLES = cycleCount();

    private static int cycleCount() {
        int value = Integer.getInteger("wsverify.cycles", 10);
        return Math.max(1, Math.min(value, 200));
    }

    private record Profile(String name,
                           ContainerTestConfig config,
                           int targetJdk,
                           String shellType,
                           List<String> packers) {
        Profile(String name, ContainerTestConfig config, int targetJdk, String shellType) {
            this(name, config, targetJdk, shellType, List.of(Packers.DefaultBase64, Packers.JSP));
        }
    }

    /**
     * 每个真实中间件对应一个 profile，覆盖 7 个可从公网镜像获得的 WsHelper 实现：
     * （ApusicRawWsHelper 依赖内网镜像，不放入可提交用例。）
     * <ul>
     *     <li>TomcatRawWsHelper：Tomcat 8.5（Wrapper/NIO）、Tomcat 10.1（Jakarta）、Tomcat 5（BIO）</li>
     *     <li>JettyRawWsHelper：Jetty 9.4</li>
     *     <li>UndertowRawWsHelper：Spring Boot 2 内嵌 Undertow</li>
     *     <li>GlassFishRawWsHelper：GlassFish 5.1（Grizzly）</li>
     *     <li>ResinRawWsHelper：Resin 4.0.67</li>
     *     <li>WebLogicRawWsHelper：WebLogic 15.1.1.0</li>
     *     <li>WebSphereRawWsHelper：WebSphere Traditional 9.0.5.17</li>
     * </ul>
     * 拉不到镜像的 profile 会在执行时记为 skipped（仅查本机镜像，不触发网络拉取）。
     */
    private static final List<Profile> PROFILES = List.of(
            new Profile("tomcat-8.5-nio",
                    ContainerTestConfig.tomcat("tomcat:8-jre8")
                            .targetJdkVersion(Opcodes.V1_8)
                            .build(),
                    Opcodes.V1_8, ShellType.RAW_WEBSOCKET),
            new Profile("tomcat-10.1-jakarta",
                    ContainerTestConfig.tomcat("tomcat:10.1-jre11")
                            .warFile(ContainerTool.warJakartaFile)
                            .targetJdkVersion(Opcodes.V11)
                            .jakarta(true)
                            .build(),
                    Opcodes.V11, ShellType.JAKARTA_RAW_WEBSOCKET),
            new Profile("tomcat-5-bio",
                    ContainerTestConfig.tomcat("reajason/tomcat:5-jdk6")
                            .targetJdkVersion(Opcodes.V1_6)
                            .build(),
                    Opcodes.V1_6, ShellType.RAW_WEBSOCKET),
            new Profile("jetty-9.4",
                    ContainerTestConfig.jetty("reajason/jetty:9.4-jre8")
                            .serverVersion("7+")
                            .targetJdkVersion(Opcodes.V1_6)
                            .build(),
                    Opcodes.V1_6, ShellType.RAW_WEBSOCKET),
            new Profile("undertow-springboot2",
                    ContainerTestConfig.builder()
                            .imageName("localhost/memshell-party/springboot2-undertow:verify")
                            .server(Server.Undertow)
                            .pidScript(ContainerTool.springbootPid)
                            .targetJdkVersion(Opcodes.V1_8)
                            .contextPath("")
                            .healthCheckPath("/test")
                            // 应用跑在 JDK17，注入端点需要反射 defineClass
                            .env(java.util.Map.of("JAVA_TOOL_OPTIONS",
                                    "--add-opens=java.base/java.lang=ALL-UNNAMED"))
                            .build(),
                    Opcodes.V1_8, ShellType.RAW_WEBSOCKET, List.of(Packers.DefaultBase64)),
            new Profile("resin-4.0.67",
                    ContainerTestConfig.resin("reajason/resin:4.0.67-jdk11",
                                    "/usr/local/resin4/webapps/app.war")
                            .targetJdkVersion(Opcodes.V11)
                            .build(),
                    Opcodes.V11, ShellType.RAW_WEBSOCKET),
            new Profile("glassfish-5.1.0",
                    ContainerTestConfig.glassFish("reajason/glassfish:5.1.0",
                                    "/usr/local/glassfish5/glassfish/domains/domain1/autodeploy/app.war")
                            .targetJdkVersion(Opcodes.V1_6)
                            .build(),
                    Opcodes.V1_6, ShellType.RAW_WEBSOCKET),
            new Profile("weblogic-15.1.1.0",
                    ContainerTestConfig.webLogic("reajason/weblogic:15.1.1.0-jdk21",
                                    "/u01/oracle/user_projects/domains/domain1/autodeploy/app.war")
                            .warFile(ContainerTool.warJakartaFile)
                            .targetJdkVersion(Opcodes.V21)
                            .jakarta(true)
                            .waitStrategy(Wait.forHttp("/app")
                                    .forPort(7001)
                                    .withStartupTimeout(Duration.ofMinutes(5)))
                            .build(),
                    Opcodes.V21, ShellType.JAKARTA_RAW_WEBSOCKET),
            new Profile("websphere-9.0.5.17",
                    ContainerTestConfig.webSphere("reajason/websphere:9.0.5.17",
                                    "/opt/IBM/WebSphere/AppServer/profiles/AppSrv01/monitoredDeployableApps/servers/server1/app.war")
                            .targetJdkVersion(Opcodes.V1_6)
                            .waitStrategy(Wait.forHttp("/app/")
                                    .forPort(9080)
                                    .withStartupTimeout(Duration.ofMinutes(5)))
                            .build(),
                    Opcodes.V1_6, ShellType.RAW_WEBSOCKET)
    );

    @TestFactory
    Stream<DynamicTest> allHelpers() {
        String filter = System.getProperty("wsverify.profiles", "").trim();
        return PROFILES.stream()
                .filter(profile -> filter.isEmpty()
                        || List.of(filter.split(",")).contains(profile.name()))
                .map(profile -> dynamicTest(profile.name(), () -> verifyProfile(profile)));
    }

    /**
     * 只查本机镜像，不触发拉取：环境拉不到镜像时该 profile 记为 skipped，
     * 而不是让整轮校验失败。
     */
    private static boolean imageExists(String image) {
        try {
            DockerClientFactory.instance().client().inspectImageCmd(image).exec();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void verifyProfile(Profile profile) throws Exception {
        String image = profile.config().getImageName();
        assumeTrue(imageExists(image), "镜像不在本机，跳过 " + profile.name() + ": " + image);
        Network network = Network.newNetwork();
        GenericContainer<?> echo = null;
        GenericContainer<?> app = null;
        try {
            echo = createEcho(network);
            echo.start();
            print("[" + profile.name() + "] echo target started");

            ContainerTestConfig config = profile.config();
            app = AbstractContainerTest.buildContainer(config, network);
            app.start();
            print("[" + profile.name() + "] middleware started");

            String appUrl = ContainerTool.getRootUrl(app, config.getExposedPort()) + config.getContextPath();
            VulTool.urlIsOk(appUrl + "/");
            print("[" + profile.name() + "] business url is ok: " + appUrl);

            String wsUrl = injectWithFallback(profile, appUrl, app, echo);
            print("[" + profile.name() + "] shell ready: " + wsUrl);

            runLifecycle(profile, appUrl, wsUrl, app, echo);

            VulTool.urlIsOk(appUrl + "/");
            awaitNoPumpThreads(app);
            print("[" + profile.name() + "] PASSED");
        } catch (Throwable t) {
            String logs = safeLogs(app);
            throw new AssertionError("[" + profile.name() + "] verification failed: " + t
                    + "\n---- container logs (tail) ----\n" + tail(logs), t);
        } finally {
            stop(app);
            stop(echo);
            closeNetwork(network);
        }
    }

    /**
     * 按 profile 的 packer 顺序注入（默认 DefaultBase64 → JSP），握手失败再换下一种。
     * 不同容器的注入端点/部署目录差异较大，多路径可以避免把注入问题误判成 helper 问题。
     */
    private String injectWithFallback(Profile profile, String appUrl, GenericContainer<?> app,
                                      GenericContainer<?> echo) throws Exception {
        Throwable firstFailure = null;
        for (String packer : profile.packers()) {
            try {
                String wsUrl = inject(profile, appUrl, app, packer);
                probeTunnel(wsUrl);
                awaitNoTargetConnections(echo);
                awaitNoPumpThreads(app);
                print("[" + profile.name() + "] injected with " + packer);
                return wsUrl;
            } catch (Throwable t) {
                print("[" + profile.name() + "] " + packer + " injection/probe failed: " + t);
                if (firstFailure == null) {
                    firstFailure = t;
                }
            }
        }
        throw new AssertionError("injection failed with all packers", firstFailure);
    }

    private String inject(Profile profile, String appUrl, GenericContainer<?> app, String packer) {
        Pair<String, String> urls = ShellAssertion.getUrls(appUrl, profile.shellType(), ShellTool.Proxy, packer);
        ShellToolConfig shellToolConfig = ShellAssertion.getShellToolConfig(
                profile.shellType(), ShellTool.Proxy, packer);
        MemShellResult result = ShellAssertion.generate(urls.getRight(), profile.config().getServer(),
                profile.config().getServerVersion(), profile.shellType(), ShellTool.Proxy,
                profile.targetJdk(), shellToolConfig, packer);
        ShellAssertion.packerResultAndInject(result, appUrl, ShellTool.Proxy,
                profile.shellType(), packer, app);
        return urls.getLeft();
    }

    private void runLifecycle(Profile profile, String appUrl, String wsUrl,
                              GenericContainer<?> app, GenericContainer<?> echo) throws Exception {
        assertEquals(0, openTargetConnections(echo), "target connection exists before tunnel");

        // 同一 shell 端点上反复建连/用/关，每轮都检查目标连接和泵线程不累积
        for (int i = 0; i < CYCLES; i++) {
            RawWsClient ws = new RawWsClient(wsUrl);
            try {
                assertTrue(ws.connectBlocking(WAIT_SECONDS, TimeUnit.SECONDS),
                        "[" + profile.name() + "] websocket connect failed");
                ws.send(connectFrame());
                assertTrue(new String(ws.awaitBinary(), StandardCharsets.ISO_8859_1)
                                .startsWith("HTTP/1.1 200 Connection Established"),
                        "[" + profile.name() + "] CONNECT was not accepted");
                byte[] payload = ("echo-" + i).getBytes(StandardCharsets.UTF_8);
                ws.send(payload);
                assertArrayEquals(payload, ws.awaitBinary(),
                        "[" + profile.name() + "] tunnel did not echo payload");
                assertEquals(1, openTargetConnections(echo),
                        "[" + profile.name() + "] expected exactly one target connection");
            } finally {
                closeQuietly(ws);
            }
            awaitNoTargetConnections(echo);
            awaitNoPumpThreads(app);
        }

        // 客户端不走 close 握手直接断开，helper 也必须回收目标 socket
        RawWsClient abrupt = new RawWsClient(wsUrl);
        try {
            assertTrue(abrupt.connectBlocking(WAIT_SECONDS, TimeUnit.SECONDS),
                    "[" + profile.name() + "] abrupt websocket connect failed");
            abrupt.send(connectFrame());
            assertTrue(new String(abrupt.awaitBinary(), StandardCharsets.ISO_8859_1)
                    .startsWith("HTTP/1.1 200"), "[" + profile.name() + "] abrupt CONNECT failed");
            assertEquals(1, openTargetConnections(echo));
            abrupt.getSocket().close();
        } finally {
            closeQuietly(abrupt);
        }
        awaitNoTargetConnections(echo);
        awaitNoPumpThreads(app);

        // 建连失败：503 + 会话关闭，不留下半开目标连接
        RawWsClient failed = new RawWsClient(wsUrl);
        try {
            assertTrue(failed.connectBlocking(WAIT_SECONDS, TimeUnit.SECONDS),
                    "[" + profile.name() + "] failed-connect websocket connect failed");
            failed.send("CONNECT 127.0.0.1:1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            assertTrue(new String(failed.awaitBinary(), StandardCharsets.ISO_8859_1)
                            .startsWith("HTTP/1.1 503"),
                    "[" + profile.name() + "] expected 503 for unreachable target");
            assertTrue(failed.awaitClose(WAIT_SECONDS, TimeUnit.SECONDS),
                    "[" + profile.name() + "] failed CONNECT must close websocket");
        } finally {
            closeQuietly(failed);
        }
        awaitNoTargetConnections(echo);
        awaitNoPumpThreads(app);

        // 并发会话：各自独立目标连接，不串流
        List<RawWsClient> clients = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                RawWsClient ws = new RawWsClient(wsUrl);
                assertTrue(ws.connectBlocking(WAIT_SECONDS, TimeUnit.SECONDS),
                        "[" + profile.name() + "] concurrent connect failed");
                ws.send(connectFrame());
                assertTrue(new String(ws.awaitBinary(), StandardCharsets.ISO_8859_1)
                        .startsWith("HTTP/1.1 200"), "[" + profile.name() + "] concurrent CONNECT failed");
                clients.add(ws);
            }
            assertEquals(2, openTargetConnections(echo),
                    "[" + profile.name() + "] concurrent tunnels did not create independent target sockets");
            Set<String> echoes = new HashSet<>();
            for (int i = 0; i < clients.size(); i++) {
                byte[] payload = ("parallel-" + i).getBytes(StandardCharsets.UTF_8);
                clients.get(i).send(payload);
                String echoed = new String(clients.get(i).awaitBinary(), StandardCharsets.ISO_8859_1);
                assertTrue(echoes.add(echoed), "[" + profile.name() + "] parallel tunnels crossed payloads");
                assertEquals(new String(payload, StandardCharsets.ISO_8859_1), echoed);
            }
        } finally {
            for (RawWsClient ws : clients) {
                closeQuietly(ws);
            }
        }
        awaitNoTargetConnections(echo);
        awaitNoPumpThreads(app);
        VulTool.urlIsOk(appUrl + "/");
    }

    private void probeTunnel(String wsUrl) throws Exception {
        RawWsClient ws = new RawWsClient(wsUrl);
        try {
            assertTrue(ws.connectBlocking(WAIT_SECONDS, TimeUnit.SECONDS), "websocket connect failed");
            ws.send(connectFrame());
            assertTrue(new String(ws.awaitBinary(), StandardCharsets.ISO_8859_1)
                    .startsWith("HTTP/1.1 200 Connection Established"), "CONNECT was not accepted");
        } finally {
            closeQuietly(ws);
        }
    }

    private static GenericContainer<?> createEcho(Network network) {
        return new GenericContainer<>(DockerImageName.parse("eclipse-temurin:17.0.17_10-jdk"))
                .withNetwork(network)
                .withNetworkAliases(TARGET_HOST)
                .withExposedPorts(TARGET_PORT)
                .withCopyToContainer(MountableFile.forClasspathResource("echo/EchoServer.java", 0644),
                        "/echo/EchoServer.java")
                .withCommand("java", "/echo/EchoServer.java", String.valueOf(TARGET_PORT))
                .waitingFor(Wait.forLogMessage(".*LISTENING " + TARGET_PORT + ".*", 1)
                        .withStartupTimeout(Duration.ofMinutes(2)));
    }

    private static byte[] connectFrame() {
        return ("CONNECT " + TARGET_HOST + ":" + TARGET_PORT + "\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    private static int openTargetConnections(GenericContainer<?> echo) {
        String logs = echo.getLogs();
        return count(logs, "ACCEPT ") - count(logs, "CLOSE ");
    }

    private static int count(String text, String marker) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(marker, index)) >= 0) {
            count++;
            index += marker.length();
        }
        return count;
    }

    private static int pumpThreadCount(GenericContainer<?> app) throws Exception {
        String command = "grep -l rawws-pump /proc/[0-9]*/task/*/comm 2>/dev/null | wc -l";
        String value = app.execInContainer("sh", "-c", command).getStdout().trim();
        return Integer.parseInt(value);
    }

    private static void awaitNoTargetConnections(GenericContainer<?> echo) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        int last = -1;
        while (System.currentTimeMillis() < deadline) {
            last = openTargetConnections(echo);
            if (last == 0) {
                return;
            }
            Thread.sleep(100);
        }
        assertEquals(0, last, "target connections were not released");
    }

    private static void awaitNoPumpThreads(GenericContainer<?> app) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        int last = -1;
        while (System.currentTimeMillis() < deadline) {
            last = pumpThreadCount(app);
            if (last == 0) {
                return;
            }
            Thread.sleep(100);
        }
        assertEquals(0, last, "pump threads were not released");
    }

    private static String safeLogs(GenericContainer<?> container) {
        if (container == null) {
            return "";
        }
        try {
            return container.getLogs();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String tail(String text) {
        if (text == null) {
            return "";
        }
        int max = 12_000;
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    private static void stop(GenericContainer<?> container) {
        if (container == null) {
            return;
        }
        try {
            container.stop();
        } catch (Throwable ignored) {
        }
    }

    private static void closeNetwork(Network network) {
        try {
            network.close();
        } catch (Throwable ignored) {
        }
    }

    private static void print(String message) {
        System.out.println(message);
        System.out.flush();
    }

    private static void closeQuietly(RawWsClient ws) {
        if (ws == null) {
            return;
        }
        try {
            ws.closeBlocking();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 保持连接的二进制 websocket 客户端，按 helper 的帧协议直接收发二进制帧。
     */
    private static final class RawWsClient extends WebSocketClient {
        private final BlockingQueue<byte[]> binaries = new LinkedBlockingQueue<>();
        private final CountDownLatch closed = new CountDownLatch(1);

        RawWsClient(String url) throws Exception {
            super(new URI(url));
            setConnectionLostTimeout(0);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
        }

        @Override
        public void onMessage(String message) {
            binaries.add(message.getBytes(StandardCharsets.ISO_8859_1));
        }

        @Override
        public void onMessage(ByteBuffer message) {
            byte[] data = new byte[message.remaining()];
            message.get(data);
            binaries.add(data);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closed.countDown();
        }

        @Override
        public void onError(Exception ex) {
            closed.countDown();
        }

        byte[] awaitBinary() throws InterruptedException {
            byte[] value = binaries.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(value, "timed out waiting for binary frame");
            return value;
        }

        boolean awaitClose(long timeout, TimeUnit unit) throws InterruptedException {
            return closed.await(timeout, unit);
        }
    }
}
