package com.expensetracker.api;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.expensetracker.util.JwtUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

// Matches "GET /api/expenses/{id}" style routes, checks the login token, and turns
// exceptions into JSON errors like {"error": "Amount must be greater than 0"}.
public final class Router implements HttpHandler {

    @FunctionalInterface
    public interface Handler {
        void handle(Ctx ctx) throws Exception;
    }

    private record Route(String method, List<String> segments, Handler handler, boolean authRequired) {}

    private final List<Route> routes = new ArrayList<>();
    private final JwtUtil jwt;

    public Router(JwtUtil jwt) {
        this.jwt = jwt;
    }

    // Routes that need a logged-in user
    public void get(String pattern, Handler h)    { add("GET", pattern, h, true); }
    public void post(String pattern, Handler h)   { add("POST", pattern, h, true); }
    public void put(String pattern, Handler h)    { add("PUT", pattern, h, true); }
    public void delete(String pattern, Handler h) { add("DELETE", pattern, h, true); }

    // Routes anyone can call (login, register, health check)
    public void publicGet(String pattern, Handler h)  { add("GET", pattern, h, false); }
    public void publicPost(String pattern, Handler h) { add("POST", pattern, h, false); }

    private void add(String method, String pattern, Handler handler, boolean auth) {
        routes.add(new Route(method, split(pattern), handler, auth));
    }

    @Override
    public void handle(HttpExchange exchange) {
        long start = System.nanoTime();
        int status = 500;
        try {
            status = dispatch(exchange);
        } catch (ApiException e) {
            status = sendError(exchange, e.status(), e.getMessage());
        } catch (IllegalArgumentException e) {
            status = sendError(exchange, 400, e.getMessage());      // validation errors from the services
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("22")) {   // e.g. number too large
                status = sendError(exchange, 400, "One of the values is invalid or too large");
            } else {
                e.printStackTrace();
                status = sendError(exchange, 500, "Something went wrong on the server");
            }
        } catch (Exception e) {
            e.printStackTrace();
            status = sendError(exchange, 500, "Something went wrong on the server");
        } finally {
            exchange.close();
        }
        System.out.printf("%s %s -> %d (%d ms)%n", exchange.getRequestMethod(),
                exchange.getRequestURI().getRawPath(), status, (System.nanoTime() - start) / 1_000_000);
    }

    private int dispatch(HttpExchange exchange) throws Exception {
        List<String> segments = split(exchange.getRequestURI().getRawPath());
        boolean pathMatched = false;
        for (Route route : routes) {
            Map<String, String> params = match(route.segments(), segments);
            if (params == null) {
                continue;
            }
            pathMatched = true;
            if (!route.method().equals(exchange.getRequestMethod())) {
                continue;
            }
            Ctx ctx = new Ctx(exchange, params);
            if (route.authRequired()) {
                ctx.setUserId(authenticate(exchange));
            }
            route.handler().handle(ctx);
            return ctx.status();
        }
        throw pathMatched ? new ApiException(405, "Method not allowed") : new ApiException(404, "Not found");
    }

    private int authenticate(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(401, "Log in required");
        }
        return jwt.verify(header.substring(7).trim())
                .orElseThrow(() -> new ApiException(401, "Session expired, please log in again"));
    }

    private static int sendError(HttpExchange exchange, int status, String message) {
        try {
            byte[] bytes = Json.MAPPER.writeValueAsBytes(Map.of("error", message == null ? "Error" : message));
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // the client went away or a reply was already started; nothing more to do
        }
        return status;
    }

    private static List<String> split(String path) {
        return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
    }

    // Returns the {name} values if the path fits the pattern, otherwise null
    private static Map<String, String> match(List<String> pattern, List<String> actual) {
        if (pattern.size() != actual.size()) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pattern.size(); i++) {
            String p = pattern.get(i);
            String a = actual.get(i);
            if (p.startsWith("{") && p.endsWith("}")) {
                params.put(p.substring(1, p.length() - 1), URLDecoder.decode(a, StandardCharsets.UTF_8));
            } else if (!p.equals(a)) {
                return null;
            }
        }
        return params;
    }
}