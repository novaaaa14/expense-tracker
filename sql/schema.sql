CREATE TABLE users (
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    id         SERIAL PRIMARY KEY,
    user_id    INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    color      VARCHAR(7)  NOT NULL DEFAULT '#FFAEC9',
    icon       VARCHAR(30) NOT NULL DEFAULT 'tag',
    is_default BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, name)
);

-- category_id is nullable: deleting a category unlinks its expenses
CREATE TABLE expenses (
    id           SERIAL PRIMARY KEY,
    user_id      INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id  INT REFERENCES categories(id) ON DELETE SET NULL,
    amount       NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    description  VARCHAR(255),
    expense_date DATE NOT NULL DEFAULT CURRENT_DATE,
    note         VARCHAR(255),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- USP: budget threshold alerts
CREATE TABLE budgets (
    id                      SERIAL PRIMARY KEY,
    user_id                 INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id             INT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    monthly_limit           NUMERIC(10,2) NOT NULL CHECK (monthly_limit > 0),
    alert_threshold_percent INT NOT NULL DEFAULT 80
                            CHECK (alert_threshold_percent BETWEEN 1 AND 100),
    UNIQUE (user_id, category_id)
);

-- USP: recurring subscription tracking
CREATE TABLE subscriptions (
    id            SERIAL PRIMARY KEY,
    user_id       INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id   INT REFERENCES categories(id) ON DELETE SET NULL,
    name          VARCHAR(100) NOT NULL,
    amount        NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    frequency     VARCHAR(10) NOT NULL CHECK (frequency IN ('WEEKLY','MONTHLY','YEARLY')),
    next_due_date DATE NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_expenses_user_date ON expenses(user_id, expense_date);
CREATE INDEX idx_expenses_user_category ON expenses(user_id, category_id);