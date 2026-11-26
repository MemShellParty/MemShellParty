package com.reajason.javaweb.boot.config;

/**
 * Holds the verified PLATFORM credential for the current HTTP request thread.
 */
public final class PlatformAuthContext {

    private static final ThreadLocal<String> CREDENTIAL = new ThreadLocal<>();

    private PlatformAuthContext() {
    }

    public static void set(String credential) {
        if (credential == null || credential.isBlank()) {
            CREDENTIAL.remove();
            return;
        }
        CREDENTIAL.set(credential);
    }

    public static String get() {
        return CREDENTIAL.get();
    }

    public static void clear() {
        CREDENTIAL.remove();
    }
}
