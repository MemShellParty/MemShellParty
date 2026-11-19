package com.reajason.javaweb.boot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * @author ReaJason
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class WebConfig {

    @Bean
    public FilterRegistrationBean<BootAuthFilter> bootAuthFilter(
            AuthProperties properties, Environment environment, RestTemplate restTemplate) {
        FilterRegistrationBean<BootAuthFilter> registration = new FilterRegistrationBean<>(
                new BootAuthFilter(properties, environment, restTemplate));
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
}