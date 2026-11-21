-- =====================================================================
-- BankServices - FICTITIOUS DEMO DATA
--
-- Every name, e-mail, phone number, address, provider and amount below
-- is invented for testing purposes only.
--
-- MANUAL LOAD: start the backend once (without the demo profile) on an
-- empty database so that Flyway creates the tables, then run:
--     mysql -u root -p bankservices < database/seed_data.sql
-- (Identical to backend/src/main/resources/db/demo/V100__demo_data.sql,
-- which the "demo" profile loads automatically - see README.)
--
-- Demo logins (username / password):
--   admin   / Admin@2026   -> ADMIN  (back office)
--   amina   / Client@2026  -> CLIENT (2 accounts, paid + unpaid + overdue bills)
--   youssef / Client@2026  -> CLIENT
--   sara    / Client@2026  -> CLIENT
--   karim   / Client@2026  -> CLIENT (current account in authorised overdraft)
--   nadia   / Client@2026  -> CLIENT (login DISABLED -> login refused)
--   omar    / (none)       -> CLIENT (PENDING_ACTIVATION -> login refused)
--
-- Account balances are consistent with the transaction history below.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Clients
-- ---------------------------------------------------------------------
INSERT INTO `client` (`id_client`, `nom`, `prenom`, `email`, `telephone`, `adresse`) VALUES
  (1, 'El Idrissi', 'Amina',   'amina.elidrissi@example.com', '+212 6 61 23 45 67', '12 Rue Ibn Sina, Agdal, Rabat'),
  (2, 'Benali',     'Youssef', 'youssef.benali@example.com',  '+212 6 62 34 56 78', '45 Boulevard Anfa, Casablanca'),
  (3, 'Lahlou',     'Sara',    'sara.lahlou@example.com',     '+212 6 63 45 67 89', '8 Derb Sidi Bouloukat, Marrakech'),
  (4, 'Tazi',       'Karim',   'karim.tazi@example.com',      '+212 6 64 56 78 90', '27 Avenue Hassan II, Fes'),
  (5, 'Berrada',    'Nadia',   'nadia.berrada@example.com',   '+212 6 65 67 89 01', '3 Rue de la Plage, Tanger'),
  (6, 'Chraibi',    'Omar',    'omar.chraibi@example.com',    '+212 6 66 78 90 12', '19 Avenue Mohammed V, Agadir');

-- ---------------------------------------------------------------------
-- Online banking logins (passwords are BCrypt hashes)
--   $2a$10$PFe/... = Admin@2026      $2a$10$gnyy... = Client@2026
-- ---------------------------------------------------------------------
INSERT INTO `utilisateur` (`id_utilisateur`, `username`, `password`, `role`, `statut`, `id_client`) VALUES
  (1, 'admin',   '$2a$10$PFe/zAEcYGLCmtQNBoJwEOgmv0zdNxxSRxf.yRG7OUmfkfHb0lTni', 'ADMIN',  'ACTIVE',             NULL),
  (2, 'amina',   '$2a$10$gnyywYrV014c7FjNmhjJ1ucoHAA9fPQeKQRs.xenhYRrW6ORZyKeq', 'CLIENT', 'ACTIVE',             1),
  (3, 'youssef', '$2a$10$gnyywYrV014c7FjNmhjJ1ucoHAA9fPQeKQRs.xenhYRrW6ORZyKeq', 'CLIENT', 'ACTIVE',             2),
  (4, 'sara',    '$2a$10$gnyywYrV014c7FjNmhjJ1ucoHAA9fPQeKQRs.xenhYRrW6ORZyKeq', 'CLIENT', 'ACTIVE',             3),
  (5, 'karim',   '$2a$10$gnyywYrV014c7FjNmhjJ1ucoHAA9fPQeKQRs.xenhYRrW6ORZyKeq', 'CLIENT', 'ACTIVE',             4),
  (6, 'nadia',   '$2a$10$gnyywYrV014c7FjNmhjJ1ucoHAA9fPQeKQRs.xenhYRrW6ORZyKeq', 'CLIENT', 'DISABLED',           5),
  -- Pending activation: this hash matches no known password; the client must
  -- set one through the activation link (admin: "send activation e-mail").
  (7, 'omar',    '$2a$10$yy2pY5R95rovGXxlk5pbHuwC2BaDyoUEmcT3OfyhZxyykaV6f12/a', 'CLIENT', 'PENDING_ACTIVATION', 6);

-- ---------------------------------------------------------------------
-- Bank accounts (single table: COURANT = current, EPARGNE = savings)
-- ---------------------------------------------------------------------
INSERT INTO `compte_bancaire`
  (`id_compte`, `numero`, `solde`, `type_compte`, `decouvert_autorise`, `taux_interet`, `date_creation`, `id_client`, `statut`) VALUES
  (1, 'BS-CC-100001',  9637.40, 'COURANT', 2000.00, 0.00, '2026-09-02', 1, 'ACTIVE'),
  (2, 'BS-EP-100002', 23000.00, 'EPARGNE',    0.00, 3.50, '2026-09-02', 1, 'ACTIVE'),
  (3, 'BS-CC-100003',  6751.00, 'COURANT', 1000.00, 0.00, '2026-09-10', 2, 'ACTIVE'),
  (4, 'BS-EP-100004', 12500.00, 'EPARGNE',    0.00, 2.75, '2026-09-10', 2, 'ACTIVE'),
  (5, 'BS-CC-100005',  4400.00, 'COURANT',  500.00, 0.00, '2026-09-18', 3, 'ACTIVE'),
  (6, 'BS-CC-100006',  -300.00, 'COURANT', 1500.00, 0.00, '2026-09-25', 4, 'ACTIVE'),
  (7, 'BS-EP-100007',     0.00, 'EPARGNE',    0.00, 3.00, '2026-09-25', 4, 'CLOSED'),
  (8, 'BS-CC-100008',  6700.00, 'COURANT',    0.00, 0.00, '2026-10-01', 5, 'ACTIVE'),
  (9, 'BS-CC-100009',     0.00, 'COURANT',    0.00, 0.00, '2026-11-15', 6, 'ACTIVE');

-- ---------------------------------------------------------------------
-- Transactions (DEPOT = deposit, RETRAIT = withdrawal, VIREMENT = transfer,
-- PAIEMENT_FACTURE = bill payment)
-- ---------------------------------------------------------------------
INSERT INTO `transaction_bancaire`
  (`id_transaction`, `type_transaction`, `montant`, `date_transaction`, `description`, `statut`, `id_compte_source`, `id_compte_destination`) VALUES
  ( 1, 'DEPOT',            15000.00, '2026-09-02 10:15:00', 'Initial deposit',                           'VALIDEE', NULL, 1),
  ( 2, 'DEPOT',            20000.00, '2026-09-02 10:20:00', 'Savings opening deposit',                   'VALIDEE', NULL, 2),
  ( 3, 'DEPOT',             8000.00, '2026-09-10 09:05:00', 'Salary September',                          'VALIDEE', NULL, 3),
  ( 4, 'DEPOT',            12500.00, '2026-09-10 09:10:00', 'Savings opening deposit',                   'VALIDEE', NULL, 4),
  ( 5, 'DEPOT',             3500.00, '2026-09-18 14:30:00', 'Initial deposit',                           'VALIDEE', NULL, 5),
  ( 6, 'DEPOT',             6000.00, '2026-09-25 11:00:00', 'Initial deposit',                           'VALIDEE', NULL, 6),
  ( 7, 'DEPOT',             1000.00, '2026-09-25 11:05:00', 'Savings opening deposit',                   'VALIDEE', NULL, 7),
  ( 8, 'DEPOT',             4200.00, '2026-10-01 16:45:00', 'Initial deposit',                           'VALIDEE', NULL, 8),
  ( 9, 'RETRAIT',           1200.00, '2026-10-05 12:00:00', 'ATM withdrawal',                            'VALIDEE', 1,    NULL),
  (10, 'VIREMENT',          3000.00, '2026-10-08 08:30:00', 'Monthly savings',                           'VALIDEE', 1,    2),
  (11, 'VIREMENT',           750.00, '2026-10-12 19:20:00', 'Shared holiday rental',                     'VALIDEE', 1,    3),
  (12, 'RETRAIT',            500.00, '2026-10-14 13:10:00', 'Cash withdrawal',                           'VALIDEE', 3,    NULL),
  (13, 'VIREMENT',          1200.00, '2026-10-20 10:00:00', 'Freelance design work',                     'VALIDEE', 3,    5),
  (14, 'RETRAIT',            300.00, '2026-10-22 17:40:00', 'ATM withdrawal',                            'VALIDEE', 5,    NULL),
  (15, 'VIREMENT',          2500.00, '2026-10-28 09:15:00', 'Car repair reimbursement',                  'VALIDEE', 6,    8),
  (16, 'RETRAIT',           3800.00, '2026-11-03 15:25:00', 'Furniture purchase (overdraft)',            'VALIDEE', 6,    NULL),
  (17, 'RETRAIT',           1000.00, '2026-11-04 10:00:00', 'Withdrawal before account closure',         'VALIDEE', 7,    NULL),
  (18, 'PAIEMENT_FACTURE',   412.60, '2026-11-12 18:02:00', 'Bill payment ELEC-2026-10-0001 - VoltaGrid Energy',  'VALIDEE', 1, NULL),
  (19, 'PAIEMENT_FACTURE',   299.00, '2026-11-14 20:47:00', 'Bill payment NET-2026-10-0004 - NetWave Fibre',      'VALIDEE', 3, NULL);

-- ---------------------------------------------------------------------
-- Bills issued to clients (fictitious providers)
-- An UNPAID bill whose due date is in the past is shown as "overdue".
-- ---------------------------------------------------------------------
INSERT INTO `bill`
  (`id_bill`, `reference`, `provider`, `category`, `amount`, `issue_date`, `due_date`, `status`, `id_client`, `created_at`, `paid_at`) VALUES
  (1, 'ELEC-2026-10-0001', 'VoltaGrid Energy',     'ELECTRICITY', 412.60, '2026-10-25', '2026-11-20', 'PAID',      1, '2026-10-25 08:00:00', '2026-11-12 18:02:00'),
  (2, 'WAT-2026-11-0002',  'ClearSpring Water',    'WATER',       186.40, '2026-11-10', '2026-12-10', 'UNPAID',    1, '2026-11-10 08:00:00', NULL),
  (3, 'NET-2026-10-0003',  'NetWave Fibre',        'INTERNET',    349.00, '2026-10-05', '2026-11-05', 'UNPAID',    1, '2026-10-05 08:00:00', NULL),
  (4, 'NET-2026-10-0004',  'NetWave Fibre',        'INTERNET',    299.00, '2026-10-20', '2026-11-20', 'PAID',      2, '2026-10-20 08:00:00', '2026-11-14 20:47:00'),
  (5, 'TEL-2026-11-0005',  'TeleLink Mobile',      'TELEPHONE',   150.00, '2026-11-15', '2026-12-15', 'UNPAID',    2, '2026-11-15 08:00:00', NULL),
  (6, 'ELEC-2026-11-0006', 'VoltaGrid Energy',     'ELECTRICITY', 275.30, '2026-11-05', '2026-12-05', 'UNPAID',    3, '2026-11-05 08:00:00', NULL),
  (7, 'OTH-2026-10-0007',  'CityPark Parking',     'OTHER',        90.00, '2026-10-01', '2026-10-31', 'CANCELLED', 3, '2026-10-01 08:00:00', NULL),
  (8, 'WAT-2026-11-0008',  'ClearSpring Water',    'WATER',       120.75, '2026-11-01', '2026-11-30', 'UNPAID',    4, '2026-11-01 08:00:00', NULL),
  (9, 'ELEC-2026-11-0009', 'VoltaGrid Energy',     'ELECTRICITY', 530.00, '2026-11-18', '2026-12-20', 'UNPAID',    5, '2026-11-18 08:00:00', NULL);

-- ---------------------------------------------------------------------
-- Bill payments (one per PAID bill, linked to its debit transaction)
-- ---------------------------------------------------------------------
INSERT INTO `bill_payment`
  (`id_bill_payment`, `reference`, `id_bill`, `id_compte`, `id_transaction`, `amount`, `payment_date`, `status`) VALUES
  (1, 'PAY-20261112-K7Q2M9XA', 1, 1, 18, 412.60, '2026-11-12 18:02:00', 'COMPLETED'),
  (2, 'PAY-20261114-R4T8B2LC', 4, 3, 19, 299.00, '2026-11-14 20:47:00', 'COMPLETED');
