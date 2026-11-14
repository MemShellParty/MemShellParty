package com.reajason.javaweb.boot.mcp;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class RestBaseUrlProvider {

    public String get() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
    }
}
