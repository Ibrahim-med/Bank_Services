-- =====================================================================
-- BankServices - least-privilege MySQL accounts
--
-- Run ONCE as an administrator (e.g. root in MySQL Workbench), after
-- replacing the two passwords. Nothing here modifies tables or data.
--
--   bankservices_app      : used by the application at runtime.
--                           Data access only (SELECT/INSERT/UPDATE/DELETE),
--                           no DDL, no FILE, no access to other databases.
--   bankservices_migrator : used only by Flyway at startup to apply schema
--                           migrations (needs DDL on the bankservices schema).
--
-- Then configure the application (environment variables / .env):
--   DB_USERNAME=bankservices_app        DB_PASSWORD=<app password>
--   FLYWAY_USERNAME=bankservices_migrator FLYWAY_PASSWORD=<migrator password>
--
-- and stop using root for the application. Verified: the application starts,
-- validates the schema and runs its banking operations with exactly these
-- privileges (SELECT ... FOR UPDATE row locks only need SELECT + UPDATE).
-- =====================================================================

CREATE USER IF NOT EXISTS 'bankservices_app'@'localhost'
    IDENTIFIED BY '<REPLACE-WITH-A-LONG-RANDOM-PASSWORD>';

GRANT SELECT, INSERT, UPDATE, DELETE
    ON `bankservices`.* TO 'bankservices_app'@'localhost';

CREATE USER IF NOT EXISTS 'bankservices_migrator'@'localhost'
    IDENTIFIED BY '<REPLACE-WITH-ANOTHER-LONG-RANDOM-PASSWORD>';

-- DDL needed by the migrations (tables, columns, indexes, foreign keys, CHECK
-- constraints) plus DML for Flyway's own history table.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON `bankservices`.* TO 'bankservices_migrator'@'localhost';

-- Check:
-- SHOW GRANTS FOR 'bankservices_app'@'localhost';
-- SHOW GRANTS FOR 'bankservices_migrator'@'localhost';
