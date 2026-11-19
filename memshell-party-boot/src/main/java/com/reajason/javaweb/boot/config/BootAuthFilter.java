package com.reajason.javaweb.boot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Enumeration;

public class BootAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BootAuthFilter.class);
    private static final String ADMIN_USERNAME = "admin";
    private static final String MODE_PROPERTY = "memshell-party.auth.mode";
    private static final String PLATFORM_PREFIX = "memshell-party.auth.platform.";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String ADMIN_CHALLENGE = "Basic realm=\"memshell-party\", charset=\"UTF-8\"";

    private final AuthProperties.Mode mode;
    private final String adminPassword;
    private final AuthProperties.Scope restScope;
    private final AuthProperties.Scope mcpScope;
    private final RestTemplate restTemplate;

    public BootAuthFilter(AuthProperties properties, Environment environment, RestTemplate restTemplate) {
        String configuredMode = environment.getProperty(MODE_PROPERTY);
        if (configuredMode != null && configuredMode.isBlank()) {
            throw new IllegalArgumentException(MODE_PROPERTY + " must be OFF, ADMIN, or PLATFORM");
        }
        this.mode = properties.getMode();
        this.restTemplate = restTemplate;

        if (mode == null) {
            throw new IllegalArgumentException(MODE_PROPERTY + " must be OFF, ADMIN, or PLATFORM");
        }
        if (mode == AuthProperties.Mode.PLATFORM) {
            AuthProperties.Platform platform = properties.getPlatform() == null
                    ? new AuthProperties.Platform()
                    : properties.getPlatform();
            validatePlatform(platform);
            this.restScope = platform.getRest();
            this.mcpScope = platform.getMcp();
        } else {
            this.restScope = null;
            this.mcpScope = null;
        }

        if (mode == AuthProperties.Mode.ADMIN) {
            byte[] passwordBytes = new byte[32];
            new SecureRandom().nextBytes(passwordBytes);
            this.adminPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(passwordBytes);
            log.info("Generated admin password for user 'admin': {}", adminPassword);
        } else {
            this.adminPassword = null;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return "/favicon.ico".equals(path) || path.startsWith("/assets/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (mode == AuthProperties.Mode.OFF || authenticated(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.resetBuffer();
        response.setStatus(mode == AuthProperties.Mode.ADMIN
                ? HttpServletResponse.SC_UNAUTHORIZED
                : HttpServletResponse.SC_FORBIDDEN);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (mode == AuthProperties.Mode.ADMIN) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, ADMIN_CHALLENGE);
        }
    }

    private boolean authenticated(HttpServletRequest request) {
        if (mode == AuthProperties.Mode.ADMIN) {
            return adminAuthenticated(request);
        }
        AuthProperties.Scope scope = scopeFor(request);
        String credential = credential(request, scope.getAuthParam());
        return credential != null && verify(scope, credential);
    }

    private AuthProperties.Scope scopeFor(HttpServletRequest request) {
        String path = requestPath(request);
        return "/mcp".equals(path) || path.startsWith("/mcp/") ? mcpScope : restScope;
    }

    private static String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty() || !uri.startsWith(contextPath)) {
            return uri;
        }
        return uri.substring(contextPath.length());
    }

    private static String credential(HttpServletRequest request, String authParam) {
        Enumeration<String> headers = request.getHeaders(authParam);
        if (headers != null && headers.hasMoreElements()) {
            String value = headers.nextElement();
            if (headers.hasMoreElements() || value == null || value.isBlank()) {
                return null;
            }
            return value;
        }
        return singleQueryValue(request.getQueryString(), authParam);
    }

    private static String singleQueryValue(String query, String name) {
        if (query == null) {
            return null;
        }

        String credential = null;
        int occurrences = 0;
        try {
            for (String pair : query.split("&", -1)) {
                int equals = pair.indexOf('=');
                String pairName = decodeQueryComponent(equals < 0 ? pair : pair.substring(0, equals));
                if (!name.equals(pairName)) {
                    continue;
                }
                credential = decodeQueryComponent(equals < 0 ? "" : pair.substring(equals + 1));
                occurrences++;
            }
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            return null;
        }
        if (occurrences != 1 || credential == null || credential.isBlank()) {
            return null;
        }
        return credential;
    }

    private boolean adminAuthenticated(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (headers == null || !headers.hasMoreElements()) {
            return false;
        }
        String authorization = headers.nextElement();
        if (headers.hasMoreElements() || authorization == null
                || authorization.length() <= BASIC_PREFIX.length()
                || !authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return false;
        }

        final String credentials;
        try {
            byte[] decoded = Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length()));
            credentials = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded))
                    .toString();
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            return false;
        }

        int colon = credentials.indexOf(':');
        if (colon < 0 || !ADMIN_USERNAME.equals(credentials.substring(0, colon))) {
            return false;
        }
        return MessageDigest.isEqual(
                adminPassword.getBytes(StandardCharsets.UTF_8),
                credentials.substring(colon + 1).getBytes(StandardCharsets.UTF_8));
    }

    private boolean verify(AuthProperties.Scope scope, String credential) {
        try {
            HttpHeaders headers = new HttpHeaders();
            String signHeader = trimToNull(scope.getSignHeader());
            if (signHeader != null) {
                headers.set(signHeader, credential);
            }

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    verificationUri(scope, credential),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.debug("Platform credential verification rejected by {}: status {}",
                        scope.getSignUrl(), response.getStatusCode().value());
                return false;
            }

            byte[] body = response.getBody();
            String text = body == null ? "" : new String(body, StandardCharsets.UTF_8);
            if (!text.contains(scope.getSignSuccessRes())) {
                log.debug("Platform credential verification rejected by {}: success marker missing",
                        scope.getSignUrl());
                return false;
            }
            return true;
        } catch (Exception exception) {
            log.debug("Platform credential verification failed for {}: {}",
                    scope.getSignUrl(), exception.getClass().getName());
            return false;
        }
    }

    private static URI verificationUri(AuthProperties.Scope scope, String credential) {
        String signParam = trimToNull(scope.getSignParam());
        if (signParam == null) {
            return URI.create(scope.getSignUrl());
        }

        String signUrl = scope.getSignUrl();
        StringBuilder builder = new StringBuilder(signUrl);
        char last = signUrl.charAt(signUrl.length() - 1);
        if (last != '?' && last != '&') {
            builder.append(signUrl.indexOf('?') >= 0 ? '&' : '?');
        }
        builder.append(encodeQueryComponent(signParam))
                .append('=')
                .append(encodeQueryComponent(credential));
        return URI.create(builder.toString());
    }

    private static String encodeQueryComponent(String value) {
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

    private static boolean isUnreserved(int value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-' || value == '.' || value == '_' || value == '~';
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String decodeQueryComponent(String value) throws CharacterCodingException {
        StringBuilder decoded = new StringBuilder(value.length());
        ByteArrayOutputStream percentBytes = new ByteArrayOutputStream();
        int index = 0;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (current != '%') {
                decoded.append(current == '+' ? ' ' : current);
                index++;
                continue;
            }

            percentBytes.reset();
            while (index < value.length() && value.charAt(index) == '%') {
                if (index + 2 >= value.length()) {
                    throw new IllegalArgumentException("Incomplete percent encoding");
                }
                int high = Character.digit(value.charAt(index + 1), 16);
                int low = Character.digit(value.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("Invalid percent encoding");
                }
                percentBytes.write((high << 4) + low);
                index += 3;
            }
            decoded.append(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(percentBytes.toByteArray())));
        }
        return decoded.toString();
    }

    private static void validatePlatform(AuthProperties.Platform platform) {
        if (platform.getRest() == null) {
            throw new IllegalArgumentException(PLATFORM_PREFIX + "rest must be configured in PLATFORM mode");
        }
        if (platform.getMcp() == null) {
            throw new IllegalArgumentException(PLATFORM_PREFIX + "mcp must be configured in PLATFORM mode");
        }
        validateScope(platform.getRest(), PLATFORM_PREFIX + "rest.");
        validateScope(platform.getMcp(), PLATFORM_PREFIX + "mcp.");
    }

    private static void validateScope(AuthProperties.Scope scope, String prefix) {
        String authParam = scope.getAuthParam();
        if (authParam == null || authParam.isBlank()) {
            throw new IllegalArgumentException(prefix + "auth-param must be non-blank in PLATFORM mode");
        }
        if (!isValidQueryName(authParam)) {
            throw new IllegalArgumentException(prefix
                    + "auth-param must be valid as an HTTP field-name token and query parameter name");
        }

        validateSignUrl(scope.getSignUrl(), prefix + "sign-url");

        String signParam = trimToNull(scope.getSignParam());
        String signHeader = trimToNull(scope.getSignHeader());
        if (signParam != null && signHeader != null) {
            throw new IllegalArgumentException(prefix + "sign-param and " + prefix
                    + "sign-header must not both be set in PLATFORM mode");
        }
        if (signParam == null && signHeader == null) {
            throw new IllegalArgumentException(prefix + "sign-param or " + prefix
                    + "sign-header must be set in PLATFORM mode");
        }
        if (signParam != null && !isValidQueryName(signParam)) {
            throw new IllegalArgumentException(prefix + "sign-param must be valid as a query parameter name");
        }
        if (signHeader != null && !isValidHeaderName(signHeader)) {
            throw new IllegalArgumentException(prefix + "sign-header must be valid as an HTTP field-name token");
        }

        String successMarker = scope.getSignSuccessRes();
        if (successMarker == null || successMarker.isBlank()) {
            throw new IllegalArgumentException(prefix + "sign-success-res must be non-blank in PLATFORM mode");
        }
    }

    private static void validateSignUrl(String signUrl, String property) {
        if (signUrl == null || signUrl.isBlank()) {
            throw new IllegalArgumentException(property + " must be non-blank in PLATFORM mode");
        }

        final URI uri;
        try {
            uri = new URI(signUrl);
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

    private static boolean isValidQueryName(String name) {
        for (int index = 0; index < name.length(); index++) {
            char current = name.charAt(index);
            if (current == '&' || current == '#' || !isTokenCharacter(current)) {
                return false;
            }
        }
        return !name.isEmpty();
    }

    private static boolean isValidHeaderName(String name) {
        for (int index = 0; index < name.length(); index++) {
            if (!isTokenCharacter(name.charAt(index))) {
                return false;
            }
        }
        return !name.isEmpty();
    }

    private static boolean isTokenCharacter(char value) {
        return value >= '0' && value <= '9'
                || value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || "!#$%&'*+-.^_`|~".indexOf(value) >= 0;
    }
}
