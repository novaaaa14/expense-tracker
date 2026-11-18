package com.expensetracker.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

// Where the database lives, and a small pool of connections to it.
//
// Settings are read in this order:
//   1. DATABASE_URL env var, in the form Neon gives you: postgresql://user:password@host/dbname?sslmode=require
//   2. DB_URL + DB_USER + DB_PASSWORD env vars (a normal jdbc:postgresql://... URL)
//   3. db.url / db.user / db.password in db.properties (what you use on your laptop)
public class DatabaseConfig {
    private static HikariDataSource pool;

    private DatabaseConfig() {}

    public static Connection getConnection() throws SQLException {
        return pool().getConnection();   // closing it (try-with-resources) hands it back to the pool
    }

    private static synchronized HikariDataSource pool() {
        if (pool == null) {
            pool = createPool();
        }
        return pool;
    }

    private static HikariDataSource createPool() {
        HikariConfig config = new HikariConfig();
        String databaseUrl = env("DATABASE_URL");

        if (databaseUrl != null) {
            fromDatabaseUrl(config, databaseUrl);
        } else if (env("DB_URL") != null) {
            config.setJdbcUrl(env("DB_URL"));
            config.setUsername(env("DB_USER"));
            config.setPassword(env("DB_PASSWORD"));
        } else {
            Properties props = loadProperties();
            if (props.getProperty("db.url") == null) {
                throw new IllegalStateException(
                        "No database configured. Set the DATABASE_URL environment variable, "
                      + "or add db.url / db.user / db.password to db.properties.");
            }
            config.setJdbcUrl(props.getProperty("db.url"));
            config.setUsername(props.getProperty("db.user"));
            config.setPassword(props.getProperty("db.password"));
        }

        config.setPoolName("expense-tracker");
        config.setMaximumPoolSize(5);
        // Neon puts the database to sleep when it's quiet, so don't hold idle connections open
        config.setMinimumIdle(0);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(5 * 60_000);
        // the first query after a sleep can take a few seconds while the database wakes up
        config.setConnectionTimeout(30_000);
        // don't crash the server at startup if the database is still waking up
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    // postgresql://user:password@host:port/dbname?sslmode=require  ->  jdbc url + user + password
    private static void fromDatabaseUrl(HikariConfig config, String databaseUrl) {
        URI uri = URI.create(databaseUrl.trim());
        String userInfo = uri.getUserInfo();
        if (userInfo == null || uri.getHost() == null) {
            throw new IllegalStateException("DATABASE_URL must look like postgresql://user:password@host/dbname");
        }
        int colon = userInfo.indexOf(':');
        String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
        String password = colon < 0 ? "" : userInfo.substring(colon + 1);

        String port = uri.getPort() < 0 ? "" : ":" + uri.getPort();
        String db = uri.getPath() == null ? "" : uri.getPath();   // starts with "/"
        // Neon needs an encrypted connection. Other query options from the string are ignored on purpose.
        config.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + port + db + "?sslmode=require");
        config.setUsername(user);
        config.setPassword(password);
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = DatabaseConfig.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load db.properties", e);
        }
        return props;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}