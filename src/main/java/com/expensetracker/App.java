package com.expensetracker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.expensetracker.api.ApiRoutes;
import com.expensetracker.api.Router;
import com.expensetracker.config.AppConfig;
import com.expensetracker.service.SubscriptionScheduler;
import com.expensetracker.util.JwtUtil;
import com.sun.net.httpserver.HttpServer;

public class App {
    private static final long TOKEN_LIFETIME_SECONDS = 30L * 24 * 60 * 60;   // 30 days

    public static void main(String[] args) throws IOException {
        JwtUtil jwt = new JwtUtil(AppConfig.jwtSecret(), TOKEN_LIFETIME_SECONDS);
        Router router = new Router(jwt);
        ApiRoutes.register(router, jwt);

        int port = AppConfig.port();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);   // all network interfaces
        server.createContext("/", router);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        SubscriptionScheduler.start();
        System.out.println("Expense Tracker API running on http://localhost:" + port + "/api/health");
    }
}