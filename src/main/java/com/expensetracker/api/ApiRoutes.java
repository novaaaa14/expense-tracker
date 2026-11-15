package com.expensetracker.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.expensetracker.api.Dtos.AuthResponse;
import com.expensetracker.api.Dtos.BudgetRequest;
import com.expensetracker.api.Dtos.CategoryBasic;
import com.expensetracker.api.Dtos.CategoryItem;
import com.expensetracker.api.Dtos.CategoryRequest;
import com.expensetracker.api.Dtos.CountResponse;
import com.expensetracker.api.Dtos.DayTotal;
import com.expensetracker.api.Dtos.DeletedResponse;
import com.expensetracker.api.Dtos.ExpenseRequest;
import com.expensetracker.api.Dtos.ExpenseResponse;
import com.expensetracker.api.Dtos.IdsRequest;
import com.expensetracker.api.Dtos.LoginRequest;
import com.expensetracker.api.Dtos.ProcessedResponse;
import com.expensetracker.api.Dtos.RegisterRequest;
import com.expensetracker.api.Dtos.SubscriptionRequest;
import com.expensetracker.api.Dtos.UpdateProfileRequest;
import com.expensetracker.model.AddExpenseResult;
import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Subscription;
import com.expensetracker.model.User;
import com.expensetracker.service.AuthService;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.PetService;
import com.expensetracker.service.SubscriptionService;
import com.expensetracker.util.JwtUtil;

// Every endpoint of the app, in one place. Each one just reads the request, calls a service, and replies.
public final class ApiRoutes {
    private final AuthService auth = new AuthService();
    private final ExpenseService expenses = new ExpenseService();
    private final BudgetService budgets = new BudgetService();
    private final CategoryService categories = new CategoryService();
    private final SubscriptionService subscriptions = new SubscriptionService();
    private final PetService pet = new PetService();

    private final Router router;
    private final JwtUtil jwt;

    private ApiRoutes(Router router, JwtUtil jwt) {
        this.router = router;
        this.jwt = jwt;
    }

    public static void register(Router router, JwtUtil jwt) {
        ApiRoutes routes = new ApiRoutes(router, jwt);
        routes.authRoutes();
        routes.expenseRoutes();
        routes.chartRoutes();
        routes.budgetRoutes();
        routes.categoryRoutes();
        routes.subscriptionRoutes();
        routes.petRoutes();
    }

    // ---------------------------------------------------------------- auth

    private void authRoutes() {
        router.publicGet("/api/health", ctx -> ctx.json(200, Map.of("status", "ok")));

        router.publicPost("/api/auth/register", ctx -> {
            RegisterRequest req = ctx.body(RegisterRequest.class);
            User user = auth.register(nz(req.name()), nz(req.email()), nz(req.password()));
            ctx.json(201, new AuthResponse(jwt.create(user.id()), user));
        });

        router.publicPost("/api/auth/login", ctx -> {
            LoginRequest req = ctx.body(LoginRequest.class);
            User user = auth.login(nz(req.email()), nz(req.password()))
                    .orElseThrow(() -> new ApiException(401, "Invalid email or password"));
            ctx.json(200, new AuthResponse(jwt.create(user.id()), user));
        });

        router.get("/api/me", ctx -> ctx.json(200, auth.findUser(ctx.userId())
                .orElseThrow(() -> new ApiException(401, "Account no longer exists"))));

        router.put("/api/me", ctx -> {
            UpdateProfileRequest req = ctx.body(UpdateProfileRequest.class);
            User user = auth.updateName(ctx.userId(), nz(req.name()))
                    .orElseThrow(() -> new ApiException(401, "Account no longer exists"));
            ctx.json(200, user);
        });
    }

    // ------------------------------------------------------------ expenses

    private void expenseRoutes() {
        // expense log: ?page=1&pageSize=10&categoryId=3 (categoryId optional)
        router.get("/api/expenses", ctx -> ctx.json(200, expenses.page(ctx.userId(),
                ctx.queryIntOrNull("categoryId"), ctx.queryInt("page", 1), ctx.queryInt("pageSize", 10))));

        // Daily Vault: ?date=2026-10-26 (the phone's own date)
        router.get("/api/expenses/today", ctx -> {
            LocalDate today = ctx.today();
            ctx.json(200, new DayTotal(today, expenses.totalForDay(ctx.userId(), today)));
        });

        router.post("/api/expenses", ctx -> {
            AddExpenseResult result = expenses.addExpense(toExpense(ctx, 0));
            ctx.json(201, new ExpenseResponse(result.expense(), result.alert().orElse(null)));
        });

        router.put("/api/expenses/{id}", ctx -> {
            AddExpenseResult result = expenses.updateExpense(toExpense(ctx, ctx.pathInt("id")))
                    .orElseThrow(() -> new ApiException(404, "Expense not found"));
            ctx.json(200, new ExpenseResponse(result.expense(), result.alert().orElse(null)));
        });

        router.delete("/api/expenses/{id}", ctx -> {
            if (!expenses.deleteExpense(ctx.pathInt("id"), ctx.userId())) {
                throw new ApiException(404, "Expense not found");
            }
            ctx.noContent();
        });
    }

    private Expense toExpense(Ctx ctx, int id) throws java.io.IOException {
        ExpenseRequest req = ctx.body(ExpenseRequest.class);
        if (req.description() == null || req.description().isBlank()) {
            throw new ApiException(400, "Enter an item name");
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        LocalDate date = req.date() != null ? req.date() : ctx.today();
        return new Expense(id, ctx.userId(), req.categoryId(), req.amount(),
                req.description().trim(), date, note);
    }

    // -------------------------------------------------------------- charts

    private void chartRoutes() {
        // ?month=2026-10 (defaults to the current month)
        router.get("/api/charts/month", ctx -> ctx.json(200, expenses.monthSummary(ctx.userId(),
                ctx.queryMonth("month", YearMonth.from(ctx.today())))));

        // the "Expenses: <category>" list. Leave categoryId out for the Uncategorized group.
        router.get("/api/charts/category-expenses", ctx -> ctx.json(200, expenses.recentForCategory(
                ctx.userId(), ctx.queryIntOrNull("categoryId"),
                ctx.queryMonth("month", YearMonth.from(ctx.today())), ctx.queryInt("limit", 3))));
    }

    // ------------------------------------------------------------- budgets

    private void budgetRoutes() {
        router.get("/api/budgets", ctx -> ctx.json(200, budgets.statusForMonth(ctx.userId(),
                ctx.queryMonth("month", YearMonth.from(ctx.today())))));

        // create or update the budget of one category
        router.put("/api/budgets/{categoryId}", ctx -> {
            BudgetRequest req = ctx.body(BudgetRequest.class);
            Budget saved = budgets.save(ctx.userId(), ctx.pathInt("categoryId"), req.limit(),
                    req.alertThresholdPercent() == null ? 80 : req.alertThresholdPercent());
            ctx.json(200, saved);
        });
    }

    // ---------------------------------------------------------- categories

    private void categoryRoutes() {
        router.get("/api/categories", ctx -> {
            Map<Integer, BigDecimal> limits = budgets.limitsByCategory(ctx.userId());
            List<CategoryItem> items = categories.list(ctx.userId()).stream()
                    .map(s -> new CategoryItem(s.category().id(), s.category().name(), s.category().color(),
                            s.category().icon(), s.category().isDefault(), s.itemCount(), s.totalSpent(),
                            limits.get(s.category().id())))
                    .toList();
            ctx.json(200, items);
        });

        router.post("/api/categories", ctx -> {
            CategoryRequest req = ctx.body(CategoryRequest.class);
            ctx.json(201, basic(categories.create(ctx.userId(), req.name(), req.color(), req.icon(), req.limit())));
        });

        router.put("/api/categories/{id}", ctx -> {
            CategoryRequest req = ctx.body(CategoryRequest.class);
            Category updated = categories.update(ctx.userId(), ctx.pathInt("id"),
                    req.name(), req.color(), req.icon(), req.limit())
                    .orElseThrow(() -> new ApiException(404, "Category not found"));
            ctx.json(200, basic(updated));
        });

        // for the confirm dialog: "N associated expenses will be unlinked"
        router.get("/api/categories/{id}/unlink-count", ctx ->
                ctx.json(200, new CountResponse(categories.unlinkCount(ctx.userId(), ctx.pathInt("id")))));

        // PURGE (n): default categories are skipped
        router.post("/api/categories/purge", ctx ->
                ctx.json(200, new DeletedResponse(categories.deleteMany(ctx.userId(), ids(ctx)))));
    }

    private static CategoryBasic basic(Category c) {
        return new CategoryBasic(c.id(), c.name(), c.color(), c.icon(), c.isDefault());
    }

    // ------------------------------------------------------- subscriptions

    private void subscriptionRoutes() {
        // ?date=2026-10-26 (the phone's own date, used for the countdowns)
        router.get("/api/subscriptions", ctx ->
                ctx.json(200, subscriptions.overview(ctx.userId(), ctx.today())));

        router.post("/api/subscriptions", ctx -> {
            SubscriptionRequest req = ctx.body(SubscriptionRequest.class);
            Subscription created = subscriptions.add(new Subscription(0, ctx.userId(), req.categoryId(),
                    nz(req.name()), req.amount(), req.frequency(), req.nextDueDate(), true));
            ctx.json(201, created);
        });

        // PROCESS DUE
        router.post("/api/subscriptions/process-due", ctx -> ctx.json(200,
                new ProcessedResponse(subscriptions.processDue(ctx.userId(), ctx.today()).size())));

        // DELETE (n) multi-select
        router.post("/api/subscriptions/delete", ctx ->
                ctx.json(200, new DeletedResponse(subscriptions.deleteMany(ctx.userId(), ids(ctx)))));

        router.delete("/api/subscriptions/{id}", ctx -> {
            if (!subscriptions.delete(ctx.userId(), ctx.pathInt("id"))) {
                throw new ApiException(404, "Subscription not found");
            }
            ctx.noContent();
        });
    }

    // ----------------------------------------------------------------- pet

    private void petRoutes() {
        router.get("/api/pet", ctx -> ctx.json(200, pet.status(ctx.userId(), ctx.today())));
    }

    // ------------------------------------------------------------- helpers

    private static List<Integer> ids(Ctx ctx) throws java.io.IOException {
        List<Integer> ids = ctx.body(IdsRequest.class).ids();
        if (ids == null || ids.isEmpty() || ids.size() > 200 || ids.contains(null)) {
            throw new ApiException(400, "Send between 1 and 200 ids");
        }
        return ids;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}