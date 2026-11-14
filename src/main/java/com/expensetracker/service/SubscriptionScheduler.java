package com.expensetracker.service;

import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Replaces "process due subscriptions on app startup": the server does it on its own, once at
// startup and then every hour. Safe to run repeatedly: it only handles payments that are due.
public final class SubscriptionScheduler {
    private SubscriptionScheduler() {}

    public static ScheduledExecutorService start() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "subscription-job");
            t.setDaemon(true);
            return t;
        });
        SubscriptionService service = new SubscriptionService();
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                int created = service.processAllDue(LocalDate.now());
                if (created > 0) {
                    System.out.println("Subscription job: added " + created + " expense(s)");
                }
            } catch (Exception e) {
                // an exception escaping here would silently cancel every future run
                System.err.println("Subscription job failed: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.HOURS);
        return scheduler;
    }
}