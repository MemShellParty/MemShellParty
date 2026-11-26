package com.reajason.javaweb.boot.service;

import com.reajason.javaweb.GenerationException;
import com.reajason.javaweb.InvalidInputException;
import com.reajason.javaweb.boot.config.AuthProperties;
import com.reajason.javaweb.boot.config.PlatformAuthContext;
import com.reajason.javaweb.boot.config.PlatformHttpSupport;
import com.reajason.javaweb.boot.config.RemoteOptionsProperties;
import com.reajason.javaweb.boot.config.WebConfig;
import com.reajason.javaweb.boot.dto.RemoteOption;
import com.reajason.javaweb.memshell.ToolConfigFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads, normalizes and resolves server-configured remote options for REST and MCP. */
@Service
public class RemoteOptionsService {

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final int MAX_OPTIONS = 1000;

    private final Map<String, Map<String, RemoteOptionsProperties.Source>> sources;
    private final AuthProperties.Mode authMode;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RemoteOptionsService(RemoteOptionsProperties properties, AuthProperties authProperties,
                                @Qualifier(WebConfig.REMOTE_OPTIONS_REST_TEMPLATE) RestTemplate restTemplate) {
        this.sources = indexSources(properties.getSources());
        this.authMode = authProperties.getMode();
        this.restTemplate = restTemplate;
        this.objectMapper = remoteOptionsObjectMapper();
    }

    public List<RemoteOption> listOptions(String tool, String field, RemoteOptionsChannel channel) {
        RemoteOptionsProperties.Source source = source(tool, field);
        ToolConfigFactory.ConfigParam param = remoteParam(tool, field);
        Map<String, RemoteItem> items = load(source, param, channel);
        List<RemoteOption> options = new ArrayList<>(items.size());
        for (RemoteItem item : items.values()) {
            options.add(item.option());
        }
        return options;
    }

    /**
     * Resolves scalar references only on configured remote object fields. Inline objects and all
     * ordinary parameters retain their existing binding behavior.
     */
    public Map<String, Object> resolveReferences(String tool, Map<String, Object> params,
                                                 RemoteOptionsChannel channel) {
        if (tool == null || params == null || params.isEmpty()) {
            return params;
        }
        Map<String, RemoteOptionsProperties.Source> toolSources = sources.get(tool);
        if (toolSources == null) {
            return params;
        }

        Map<String, ToolConfigFactory.ConfigParam> declared = null;
        Map<String, Object> resolved = null;
        for (RemoteOptionsProperties.Source source : toolSources.values()) {
            String field = source.getField();
            Object value = params.get(field);
            if (!(value instanceof CharSequence) && !(value instanceof Number)) {
                continue;
            }
            if (declared == null) {
                declared = new LinkedHashMap<>();
                for (ToolConfigFactory.ConfigParam param : ToolConfigFactory.describeParams(tool)) {
                    declared.put(param.getName(), param);
                }
            }
            ToolConfigFactory.ConfigParam param = declared.get(field);
            if (param == null || param.getUi() == null || !"object".equals(param.getType())) {
                continue;
            }

            String reference = value.toString();
            RemoteItem selected = load(source, param, channel).get(reference);
            if (selected == null) {
                throw new InvalidInputException("Unknown remote option for " + tool + "." + field
                        + ": " + reference);
            }
            if (resolved == null) {
                resolved = new LinkedHashMap<>(params);
            }
            resolved.put(field, objectMapper.convertValue(selected.value(), Map.class));
        }
        return resolved == null ? params : resolved;
    }

    private Map<String, RemoteItem> load(RemoteOptionsProperties.Source source,
                                         ToolConfigFactory.ConfigParam param,
                                         RemoteOptionsChannel channel) {
        JsonNode payload = fetch(source, channel);
        JsonNode items = readPath(payload, param.getUi().getItemsPath());
        if (items == null || !items.isArray()) {
            throw invalidResponse(source, "itemsPath does not identify an array");
        }
        if (items.size() > MAX_OPTIONS) {
            throw invalidResponse(source, "more than " + MAX_OPTIONS + " options");
        }

        String keyPath = "object".equals(param.getType())
                ? PlatformHttpSupport.trimToNull(param.getUi().getKeyPath())
                : PlatformHttpSupport.trimToNull(param.getUi().getValuePath());
        if (keyPath == null) {
            throw new GenerationException("Remote options keyPath/valuePath is not configured for "
                    + source.getTool() + "." + source.getField());
        }

        Map<String, RemoteItem> result = new LinkedHashMap<>();
        for (JsonNode item : items) {
            if (item == null || !item.isObject()) {
                throw invalidResponse(source, "option items must be objects");
            }
            String label = scalar(readPath(item, param.getUi().getLabelPath()));
            String identifier = scalar(readPath(item, keyPath));
            if (label == null) {
                throw invalidResponse(source, "option labels must be strings or numbers");
            }
            if (identifier == null || identifier.isBlank()) {
                throw invalidResponse(source, "option identifiers must be non-blank strings or numbers");
            }
            RemoteItem remoteItem = new RemoteItem(new RemoteOption(label, identifier), item);
            if (result.putIfAbsent(identifier, remoteItem) != null) {
                throw invalidResponse(source, "duplicate option identifier: " + identifier);
            }
        }
        return result;
    }

    private JsonNode fetch(RemoteOptionsProperties.Source source, RemoteOptionsChannel channel) {
        RemoteOptionsProperties.Channel forwarding = channel == RemoteOptionsChannel.REST
                ? source.getRest() : source.getMcp();
        String authParam = forwarding == null ? null : PlatformHttpSupport.trimToNull(forwarding.getAuthParam());
        String authHeader = forwarding == null ? null : PlatformHttpSupport.trimToNull(forwarding.getAuthHeader());
        boolean authenticated = authParam != null || authHeader != null;

        String credential = null;
        if (authMode == AuthProperties.Mode.PLATFORM) {
            if (!authenticated) {
                throw new GenerationException("Remote options credential forwarding is not configured for "
                        + channel + " " + source.getTool() + "." + source.getField());
            }
            credential = PlatformAuthContext.get();
            if (credential == null) {
                throw new GenerationException("Verified PLATFORM credential is unavailable for remote options");
            }
        } else if (authenticated) {
            throw new GenerationException("Authenticated remote options require PLATFORM auth mode");
        }

        try {
            URI uri = URI.create(source.getUrl());
            if (authParam != null) {
                uri = PlatformHttpSupport.appendQueryParam(source.getUrl(), authParam, credential);
            }
            String headerName = authHeader;
            String headerValue = credential;
            byte[] body = restTemplate.execute(uri, HttpMethod.GET, request -> {
                request.getHeaders().setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                if (headerName != null) {
                    request.getHeaders().set(headerName, headerValue);
                }
            }, response -> readResponse(response, source));
            try {
                return objectMapper.readTree(body);
            } catch (RuntimeException exception) {
                throw invalidResponse(source, "response is not valid JSON");
            }
        } catch (RestClientException exception) {
            throw new GenerationException("Remote options request failed for "
                    + source.getTool() + "." + source.getField(), exception);
        }
    }

    private byte[] readResponse(ClientHttpResponse response, RemoteOptionsProperties.Source source)
            throws IOException {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new GenerationException("Remote options returned HTTP " + response.getStatusCode().value()
                    + " for " + source.getTool() + "." + source.getField());
        }
        try (InputStream input = response.getBody()) {
            byte[] body = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (body.length > MAX_RESPONSE_BYTES) {
                throw invalidResponse(source, "response exceeds " + MAX_RESPONSE_BYTES + " bytes");
            }
            return body;
        }
    }

    private ToolConfigFactory.ConfigParam remoteParam(String tool, String field) {
        for (ToolConfigFactory.ConfigParam param : ToolConfigFactory.describeParams(tool)) {
            if (param.getName().equals(field) && param.getUi() != null) {
                return param;
            }
        }
        throw new InvalidInputException("Remote options field is not declared for " + tool + "." + field);
    }

    private RemoteOptionsProperties.Source source(String tool, String field) {
        Map<String, RemoteOptionsProperties.Source> toolSources = sources.get(tool);
        RemoteOptionsProperties.Source source = toolSources == null ? null : toolSources.get(field);
        if (source == null) {
            throw new InvalidInputException("Remote options source is not configured for " + tool + "." + field);
        }
        return source;
    }

    private static Map<String, Map<String, RemoteOptionsProperties.Source>> indexSources(
            List<RemoteOptionsProperties.Source> configured) {
        Map<String, Map<String, RemoteOptionsProperties.Source>> result = new LinkedHashMap<>();
        int index = 0;
        for (RemoteOptionsProperties.Source source : configured) {
            String prefix = "memshell-party.remote-options.sources[" + index++ + "]";
            if (source == null) {
                throw new IllegalArgumentException(prefix + " must not be null");
            }
            String tool = required(source.getTool(), prefix + ".tool");
            String field = required(source.getField(), prefix + ".field");
            source.setTool(tool);
            source.setField(field);
            PlatformHttpSupport.validateHttpUrl(source.getUrl(), prefix + ".url");
            validateChannel(source.getRest(), prefix + ".rest");
            validateChannel(source.getMcp(), prefix + ".mcp");
            Map<String, RemoteOptionsProperties.Source> toolSources =
                    result.computeIfAbsent(tool, ignored -> new LinkedHashMap<>());
            if (toolSources.putIfAbsent(field, source) != null) {
                throw new IllegalArgumentException("Duplicate remote options source for " + tool + "." + field);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static void validateChannel(RemoteOptionsProperties.Channel channel, String prefix) {
        if (channel == null) {
            return;
        }
        String authParam = PlatformHttpSupport.trimToNull(channel.getAuthParam());
        String authHeader = PlatformHttpSupport.trimToNull(channel.getAuthHeader());
        if (authParam != null && authHeader != null) {
            throw new IllegalArgumentException(prefix + ".auth-param and " + prefix
                    + ".auth-header must not both be set");
        }
        if (authParam != null && !PlatformHttpSupport.isValidQueryName(authParam)) {
            throw new IllegalArgumentException(prefix + ".auth-param must be a valid query parameter name");
        }
        if (authHeader != null && !PlatformHttpSupport.isValidHeaderName(authHeader)) {
            throw new IllegalArgumentException(prefix + ".auth-header must be a valid HTTP header name");
        }
    }

    private static String required(String value, String property) {
        String trimmed = PlatformHttpSupport.trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(property + " must be non-blank");
        }
        return trimmed;
    }

    private static JsonNode readPath(JsonNode value, String path) {
        if (value == null) {
            return null;
        }
        if (path == null || path.isEmpty()) {
            return value;
        }
        JsonNode current = value;
        for (String segment : path.split("\\.", -1)) {
            if (segment.isEmpty() || current == null || !current.isObject() || !current.has(segment)) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private static String scalar(JsonNode value) {
        if (value == null || !value.isValueNode() || value.isNull() || value.isBoolean()) {
            return null;
        }
        if (!value.isTextual() && !value.isNumber()) {
            return null;
        }
        return value.asText();
    }

    private static ObjectMapper remoteOptionsObjectMapper() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(100)
                .build();
        return new ObjectMapper(JsonFactory.builder()
                .streamReadConstraints(constraints)
                .build());
    }

    private GenerationException invalidResponse(RemoteOptionsProperties.Source source, String reason) {
        return new GenerationException("Invalid remote options response for "
                + source.getTool() + "." + source.getField() + ": " + reason);
    }

    private record RemoteItem(RemoteOption option, JsonNode value) {
    }
}
