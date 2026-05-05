UPDATE iblog.users SET password_hash = '{noop}admin123' WHERE id = 1;
UPDATE iblog.users SET password_hash = '{noop}alice123' WHERE id = 2;
UPDATE iblog.users SET password_hash = '{noop}bob123' WHERE id = 3;
