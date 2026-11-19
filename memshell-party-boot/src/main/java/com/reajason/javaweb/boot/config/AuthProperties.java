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

        private Scope rest;
        private Scope mcp;

        public Scope getRest() {
            return rest;
        }

        public void setRest(Scope rest) {
            this.rest = rest;
        }

        public Scope getMcp() {
            return mcp;
        }

        public void setMcp(Scope mcp) {
            this.mcp = mcp;
        }
    }

    public static class Scope {

        private String authParam;
        private String signUrl;
        private String signParam;
        private String signHeader;
        private String signSuccessRes;

        public String getAuthParam() {
            return authParam;
        }

        public void setAuthParam(String authParam) {
            this.authParam = authParam;
        }

        public String getSignUrl() {
            return signUrl;
        }

        public void setSignUrl(String signUrl) {
            this.signUrl = signUrl;
        }

        public String getSignParam() {
            return signParam;
        }

        public void setSignParam(String signParam) {
            this.signParam = signParam;
        }

        public String getSignHeader() {
            return signHeader;
        }

        public void setSignHeader(String signHeader) {
            this.signHeader = signHeader;
        }

        public String getSignSuccessRes() {
            return signSuccessRes;
        }

        public void setSignSuccessRes(String signSuccessRes) {
            this.signSuccessRes = signSuccessRes;
        }
    }
}
