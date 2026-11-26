package com.reajason.javaweb.integration.memshell.jetty;

import com.reajason.javaweb.integration.RawWebSocketProxyNginxAssertion;
import com.reajason.javaweb.memshell.ServerType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
public class JettyRawWebSocketProxyNginxTest {
    @Container
    private static final ComposeContainer compose =
            RawWebSocketProxyNginxAssertion.createCompose("docker-compose-jetty.yaml");

    @BeforeAll
    static void inject() {
        RawWebSocketProxyNginxAssertion.inject(compose, ServerType.JETTY, "7+");
    }

    @Test
    @Order(1)
    void direct() throws Exception {
        RawWebSocketProxyNginxAssertion.assertProxy(compose, "app");
    }

    @Test
    @Order(2)
    void singleNginx() throws Exception {
        RawWebSocketProxyNginxAssertion.assertProxy(compose, "nginx1");
    }
}
