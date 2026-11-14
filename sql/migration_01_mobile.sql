-- Mobile redesign: run ONCE on an existing expense_tracker database.
-- (Fresh installs: use the updated schema.sql instead.)
BEGIN;

-- Categories: colour tag, icon key, and a flag for the four built-in ones (delete-locked)
ALTER TABLE categories
    ADD COLUMN color      VARCHAR(7)  NOT NULL DEFAULT '#FFAEC9',
    ADD COLUMN icon       VARCHAR(30) NOT NULL DEFAULT 'tag',
    ADD COLUMN is_default BOOLEAN     NOT NULL DEFAULT FALSE;

UPDATE categories SET is_default = TRUE, color = '#FFAEC9', icon = 'food'    WHERE name = 'Food';
UPDATE categories SET is_default = TRUE, color = '#6BE0D2', icon = 'transit' WHERE name = 'Transport';
UPDATE categories SET is_default = TRUE, color = '#FDF982', icon = 'game'    WHERE name = 'Entertainment';
UPDATE categories SET is_default = TRUE, color = '#C8B6FF', icon = 'bolt'    WHERE name = 'Bills';

-- Expenses: optional memo note (Edit Expense screen)
ALTER TABLE expenses ADD COLUMN note VARCHAR(255);

-- Deleting a category now UNLINKS its expenses/subscriptions instead of being blocked
ALTER TABLE expenses ALTER COLUMN category_id DROP NOT NULL;
ALTER TABLE expenses
    DROP CONSTRAINT expenses_category_id_fkey,
    ADD  CONSTRAINT expenses_category_id_fkey
         FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;

ALTER TABLE subscriptions ALTER COLUMN category_id DROP NOT NULL;
ALTER TABLE subscriptions
    DROP CONSTRAINT subscriptions_category_id_fkey,
    ADD  CONSTRAINT subscriptions_category_id_fkey
         FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;

-- Category filter + stats queries
CREATE INDEX idx_expenses_user_category ON expenses(user_id, category_id);

COMMIT;

-- Verification: every new/changed column should show up here
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE (table_name = 'categories'    AND column_name IN ('color', 'icon', 'is_default'))
   OR (table_name = 'expenses'      AND column_name IN ('note', 'category_id'))
   OR (table_name = 'subscriptions' AND column_name = 'category_id')
ORDER BY table_name, column_name;