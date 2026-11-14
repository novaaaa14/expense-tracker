package com.expensetracker.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;

// One request: path/query parameters, the JSON body, the logged-in user, and the reply helpers
public final class Ctx {
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private final Map<String, String> query;
    private int userId;
    private int status = 200;

    Ctx(HttpExchange exchange, Map<String, String> pathParams) {
        this.exchange = exchange;
        this.pathParams = pathParams;
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
    }

    void setUserId(int userId) { this.userId = userId; }
    int status() { return status; }

    public int userId() { return userId; }

    // ---- input ----

    public int pathInt(String name) {
        return parseInt(pathParams.get(name), name);
    }

    public String query(String name) {
        return query.get(name);
    }

    public int queryInt(String name, int fallback) {
        String raw = query.get(name);
        return raw == null || raw.isBlank() ? fallback : parseInt(raw, name);
    }

    public Integer queryIntOrNull(String name) {
        String raw = query.get(name);
        return raw == null || raw.isBlank() ? null : parseInt(raw, name);
    }

    public YearMonth queryMonth(String name, YearMonth fallback) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ApiException(400, name + " must look like 2026-10");
        }
    }

    // "Today" as the phone sees it (?date=2026-10-26), so the server's timezone doesn't matter.
    // Anything more than 2 days away from the server's date is rejected.
    public LocalDate today() {
        LocalDate server = LocalDate.now();
        String raw = query.get("date");
        if (raw == null || raw.isBlank()) {
            return server;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "date must look like 2026-10-26");
        }
        if (Math.abs(ChronoUnit.DAYS.between(server, date)) > 2) {
            throw new ApiException(400, "date is too far from the server's date");
        }
        return date;
    }

    public <T> T body(Class<T> type) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "Request body is too large");
        }
        if (bytes.length == 0) {
            throw new ApiException(400, "Request body is missing");
        }
        try {
            T value = Json.MAPPER.readValue(bytes, type);
            if (value == null) {
                throw new ApiException(400, "Request body is missing");
            }
            return value;
        } catch (JsonProcessingException e) {
            throw new ApiException(400, "Request body is not valid for this endpoint");
        }
    }

    // ---- output ----

    public void json(int statusCode, Object body) throws IOException {
        byte[] bytes = Json.MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        status = statusCode;
    }

    public void noContent() throws IOException {
        exchange.sendResponseHeaders(204, -1);
        status = 204;
    }

    // ---- helpers ----

    private static int parseInt(String raw, String name) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new ApiException(400, name + " must be a whole number");
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        try {
            for (String pair : raw.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                result.putIfAbsent(key, value);
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Malformed query string");
        }
        return result;
    }
}