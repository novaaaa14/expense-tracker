package com.expensetracker.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

// Server settings. Each one can come from an environment variable or from db.properties.
public final class AppConfig {
    private static final Properties props = new Properties();

    static {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load db.properties", e);
        }
    }

    private AppConfig() {}

    public static String jwtSecret() {
        String secret = value("JWT_SECRET", "jwt.secret");
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "Missing JWT secret. Set the JWT_SECRET environment variable, or add a line "
                  + "jwt.secret=<at least 32 random characters> to db.properties. "
                  + "Generate one with: openssl rand -base64 48");
        }
        return secret;
    }

    public static int port() {
        String port = value("PORT", "server.port");
        return port == null ? 8080 : Integer.parseInt(port.trim());
    }

    private static String value(String envName, String propName) {
        String fromEnv = System.getenv(envName);
        return fromEnv != null && !fromEnv.isBlank() ? fromEnv : props.getProperty(propName);
    }
}