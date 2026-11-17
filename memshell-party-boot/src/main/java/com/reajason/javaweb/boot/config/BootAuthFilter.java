package com.reajason.javaweb.boot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;

public class BootAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BootAuthFilter.class);
    private static final String ADMIN_USERNAME = "admin";
    private static final String MODE_PROPERTY = "memshell-party.auth.mode";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String ADMIN_CHALLENGE = "Basic realm=\"memshell-party\", charset=\"UTF-8\"";

    private final AuthProperties.Mode mode;
    private final String adminPassword;
    private final String platformSecret;
    private final String platformName;

    public BootAuthFilter(AuthProperties properties, Environment environment) {
        String configuredMode = environment.getProperty(MODE_PROPERTY);
        if (configuredMode != null && configuredMode.isBlank()) {
            throw new IllegalArgumentException(MODE_PROPERTY + " must be OFF, ADMIN, or PLATFORM");
        }
        this.mode = properties.getMode();
        AuthProperties.Platform platform = properties.getPlatform();
        String configuredPlatformSecret = platform == null ? null : platform.getSecret();
        this.platformName = platform == null ? null : platform.getName();

        if (mode == null) {
            throw new IllegalArgumentException(MODE_PROPERTY + " must be OFF, ADMIN, or PLATFORM");
        }
        if (mode == AuthProperties.Mode.PLATFORM) {
            validatePlatform(configuredPlatformSecret);
        }

        if (mode == AuthProperties.Mode.PLATFORM && configuredPlatformSecret == null) {
            byte[] secretBytes = new byte[32];
            new SecureRandom().nextBytes(secretBytes);
            this.platformSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
            log.info("Generated platform credential for '{}': {}", platformName, platformSecret);
        } else {
            this.platformSecret = configuredPlatformSecret;
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
        String path = request.getRequestURI().substring(request.getContextPath().length());
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
        Enumeration<String> headers = request.getHeaders(platformName);
        if (headers != null && headers.hasMoreElements()) {
            return headerAuthenticated(headers);
        }
        return queryAuthenticated(request);
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

    private boolean headerAuthenticated(Enumeration<String> headers) {
        String credential = headers.nextElement();
        return !headers.hasMoreElements() && validPlatformCredential(credential);
    }

    private boolean queryAuthenticated(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null) {
            return false;
        }

        List<String> credentials = new ArrayList<>(1);
        try {
            for (String pair : query.split("&", -1)) {
                int equals = pair.indexOf('=');
                String name = decodeQueryComponent(equals < 0 ? pair : pair.substring(0, equals));
                if (platformName.equals(name)) {
                    credentials.add(decodeQueryComponent(equals < 0 ? "" : pair.substring(equals + 1)));
                }
            }
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            return false;
        }
        return credentials.size() == 1 && validPlatformCredential(credentials.get(0));
    }

    private boolean validPlatformCredential(String credential) {
        return credential != null && !credential.isBlank()
                && MessageDigest.isEqual(
                        platformSecret.getBytes(StandardCharsets.UTF_8),
                        credential.getBytes(StandardCharsets.UTF_8));
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

    private void validatePlatform(String configuredSecret) {
        if (configuredSecret != null && configuredSecret.isBlank()) {
            throw new IllegalArgumentException("memshell-party.auth.platform.secret must be nonblank when configured in PLATFORM mode");
        }
        if (platformName == null || platformName.isBlank()) {
            throw new IllegalArgumentException("memshell-party.auth.platform.name must be nonblank in PLATFORM mode");
        }
        if (!isValidCredentialName(platformName)) {
            throw new IllegalArgumentException("memshell-party.auth.platform.name must be valid as an HTTP field-name token and query parameter name");
        }
    }

    private static boolean isValidCredentialName(String name) {
        for (int index = 0; index < name.length(); index++) {
            char current = name.charAt(index);
            if (current == '&' || current == '#' || !isTokenCharacter(current)) {
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
