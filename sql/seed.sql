INSERT INTO users (name, email, password_hash)
VALUES ('Test User', 'test@example.com', 'placeholder_hash');

INSERT INTO categories (user_id, name) VALUES
(1, 'Food'), (1, 'Transport'), (1, 'Entertainment'), (1, 'Bills');

INSERT INTO expenses (user_id, category_id, amount, description, expense_date) VALUES
(1, 1, 250.00, 'Lunch with friends', '2026-10-10'),
(1, 1, 120.00, 'Groceries',          '2026-10-12'),
(1, 2,  80.00, 'Metro card recharge','2026-10-11'),
(1, 3, 499.00, 'Movie night',        '2026-10-13');