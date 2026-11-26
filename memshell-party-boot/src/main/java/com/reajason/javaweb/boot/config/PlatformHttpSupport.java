package com.reajason.javaweb.boot.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Shared HTTP URL validation and query/header helpers for platform auth and MCP remote options.
 */
public final class PlatformHttpSupport {

    private PlatformHttpSupport() {
    }

    public static void validateHttpUrl(String url, String property) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(property + " must be non-blank");
        }

        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(property + " must be a valid absolute http or https URL");
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || scheme == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null
                || uri.getHost().isBlank()) {
            throw new IllegalArgumentException(property + " must be a valid absolute http or https URL");
        }
        if (uri.getFragment() != null) {
            throw new IllegalArgumentException(property + " must not contain a fragment");
        }
    }

    public static URI appendQueryParam(String baseUrl, String paramName, String paramValue) {
        StringBuilder builder = new StringBuilder(baseUrl);
        char last = baseUrl.charAt(baseUrl.length() - 1);
        if (last != '?' && last != '&') {
            builder.append(baseUrl.indexOf('?') >= 0 ? '&' : '?');
        }
        builder.append(encodeQueryComponent(paramName))
                .append('=')
                .append(encodeQueryComponent(paramValue));
        return URI.create(builder.toString());
    }

    public static String encodeQueryComponent(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte valueByte : bytes) {
            int unsigned = valueByte & 0xFF;
            if (isUnreserved(unsigned)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%')
                        .append(Character.toUpperCase(Character.forDigit((unsigned >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(unsigned & 0xF, 16)));
            }
        }
        return encoded.toString();
    }

    public static boolean isValidQueryName(String name) {
        for (int index = 0; index < name.length(); index++) {
            char current = name.charAt(index);
            if (current == '&' || current == '#' || !isTokenCharacter(current)) {
                return false;
            }
        }
        return !name.isEmpty();
    }

    public static boolean isValidHeaderName(String name) {
        for (int index = 0; index < name.length(); index++) {
            if (!isTokenCharacter(name.charAt(index))) {
                return false;
            }
        }
        return !name.isEmpty();
    }

    public static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isUnreserved(int value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-' || value == '.' || value == '_' || value == '~';
    }

    private static boolean isTokenCharacter(char value) {
        return value >= '0' && value <= '9'
                || value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || "!#$%&'*+-.^_`|~".indexOf(value) >= 0;
    }
}
