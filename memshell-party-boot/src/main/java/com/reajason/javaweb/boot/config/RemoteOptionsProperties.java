package com.reajason.javaweb.boot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side data sources for shell tool {@code @RemoteOptions} fields.
 */
@ConfigurationProperties("memshell-party.remote-options")
public class RemoteOptionsProperties {

    private List<Source> sources = new ArrayList<>();

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    public static class Source {

        private String tool;
        private String field;
        private String url;
        private Channel rest;
        private Channel mcp;

        public String getTool() {
            return tool;
        }

        public void setTool(String tool) {
            this.tool = tool;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Channel getRest() {
            return rest;
        }

        public void setRest(Channel rest) {
            this.rest = rest;
        }

        public Channel getMcp() {
            return mcp;
        }

        public void setMcp(Channel mcp) {
            this.mcp = mcp;
        }
    }

    /**
     * Outgoing credential placement for one caller channel. The value always comes from the
     * verified request, never from configuration.
     */
    public static class Channel {

        private String authParam;
        private String authHeader;

        public String getAuthParam() {
            return authParam;
        }

        public void setAuthParam(String authParam) {
            this.authParam = authParam;
        }

        public String getAuthHeader() {
            return authHeader;
        }

        public void setAuthHeader(String authHeader) {
            this.authHeader = authHeader;
        }
    }
}
