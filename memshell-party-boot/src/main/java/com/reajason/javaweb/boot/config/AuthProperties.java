package com.reajason.javaweb.boot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("memshell-party.auth")
public class AuthProperties {

    private Mode mode = Mode.OFF;
    private Platform platform = new Platform();

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public enum Mode {
        OFF,
        ADMIN,
        PLATFORM
    }

    public static class Platform {

        private String secret;
        private String name;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
