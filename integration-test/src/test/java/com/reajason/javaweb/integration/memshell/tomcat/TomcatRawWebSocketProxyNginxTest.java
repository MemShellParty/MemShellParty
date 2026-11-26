package com.reajason.javaweb.integration.memshell.tomcat;

import com.reajason.javaweb.integration.RawWebSocketProxyNginxAssertion;
import com.reajason.javaweb.memshell.ServerType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
public class TomcatRawWebSocketProxyNginxTest {
    @Container
    private static final ComposeContainer compose =
            RawWebSocketProxyNginxAssertion.createCompose("docker-compose-tomcat.yaml");

    @BeforeAll
    static void inject() {
        RawWebSocketProxyNginxAssertion.inject(compose, ServerType.TOMCAT, null);
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

    @Test
    @Order(3)
    void nestedNginx() throws Exception {
        RawWebSocketProxyNginxAssertion.assertProxy(compose, "nginx2");
    }

    @Test
    @Order(4)
    void tripleNginx() throws Exception {
        RawWebSocketProxyNginxAssertion.assertProxy(compose, "nginx3");
    }
}
