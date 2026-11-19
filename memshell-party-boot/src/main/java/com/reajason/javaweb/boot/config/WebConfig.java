package com.reajason.javaweb.boot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author ReaJason
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class WebConfig {

    private static final SSLSocketFactory PLATFORM_SIGN_SOCKET_FACTORY = trustAllSocketFactory();

    @Bean
    public FilterRegistrationBean<BootAuthFilter> bootAuthFilter(
            AuthProperties properties, Environment environment) {
        FilterRegistrationBean<BootAuthFilter> registration = new FilterRegistrationBean<>(
                new BootAuthFilter(properties, environment, platformSignRestTemplate()));
        registration.setName("bootAuthFilter");
        registration.setUrlPatterns(List.of("/*"));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }

    private static RestTemplate platformSignRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                if (connection instanceof HttpsURLConnection https) {
                    https.setSSLSocketFactory(PLATFORM_SIGN_SOCKET_FACTORY);
                    https.setHostnameVerifier((hostname, session) -> true);
                }
            }
        };
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }

    private static SSLSocketFactory trustAllSocketFactory() {
        TrustManager[] trustAll = {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return context.getSocketFactory();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot initialize platform sign TLS context", exception);
        }
    }
}
