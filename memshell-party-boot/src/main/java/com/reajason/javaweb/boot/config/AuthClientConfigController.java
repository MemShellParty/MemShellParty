package com.reajason.javaweb.boot.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthClientConfigController {

    private final AuthProperties authProperties;

    public AuthClientConfigController(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @GetMapping("/api/auth/client-config")
    public ResponseEntity<ClientConfigResponse> clientConfig(HttpServletRequest request) {
        AuthProperties.Mode mode = authProperties.getMode();
        String authorization = mode == AuthProperties.Mode.ADMIN
                ? request.getHeader(HttpHeaders.AUTHORIZATION)
                : null;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ClientConfigResponse(mode, authorization));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClientConfigResponse(AuthProperties.Mode mode, String authorization) {
    }
}
