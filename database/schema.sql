-- =====================================================================
-- BankServices - DATABASE SCHEMA (MySQL 8, InnoDB, utf8mb4)
--
-- 8 tables. Exported with mysqldump --no-data from a database built by
-- the Flyway migrations (backend/src/main/resources/db/migration V1..V3),
-- which remain the source of truth: the backend creates this schema
-- AUTOMATICALLY on startup - you do not need to run this file to use
-- the application.
--
-- Use this file to read / review the schema or to build a stand-alone
-- copy for SQL exercises:
--     mysql -u root -p < database/schema.sql
--     mysql -u root -p bankservices_schema < database/seed_data.sql
-- Do NOT point the backend at a database created this way: Flyway would
-- try to re-apply migrations V2/V3 and stop with an error.
--
-- Diagram: database/er-diagram.png  |  Data dictionary: README.md
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `bankservices_schema`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `bankservices_schema`;

-- ---------------------------------------------------------------------
-- client : bank customer (personal information)
-- ---------------------------------------------------------------------
CREATE TABLE `client` (
  `id_client` int NOT NULL AUTO_INCREMENT,
  `nom` varchar(50) NOT NULL,
  `prenom` varchar(50) NOT NULL,
  `email` varchar(100) DEFAULT NULL,
  `telephone` varchar(20) DEFAULT NULL,
  `adresse` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_client`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- utilisateur : online-banking login (role ADMIN or CLIENT).
-- Password = BCrypt hash. id_client is NULL for administrators and
-- UNIQUE, so a client has at most one login (1 - 0..1).
-- ---------------------------------------------------------------------
CREATE TABLE `utilisateur` (
  `id_utilisateur` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` varchar(20) NOT NULL,
  `statut` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `id_client` int DEFAULT NULL,
  PRIMARY KEY (`id_utilisateur`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `id_client` (`id_client`),
  CONSTRAINT `fk_utilisateur_client` FOREIGN KEY (`id_client`) REFERENCES `client` (`id_client`),
  CONSTRAINT `chk_utilisateur_statut` CHECK ((`statut` in (_utf8mb4'PENDING_ACTIVATION',_utf8mb4'ACTIVE',_utf8mb4'DISABLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- compte_bancaire : bank accounts (JPA SINGLE_TABLE inheritance).
-- type_compte = COURANT (current, may have an overdraft) or EPARGNE
-- (savings, may have an interest rate). chk_solde forbids a balance
-- below the authorised overdraft.
-- ---------------------------------------------------------------------
CREATE TABLE `compte_bancaire` (
  `id_compte` int NOT NULL AUTO_INCREMENT,
  `numero` varchar(30) NOT NULL,
  `solde` decimal(15,2) NOT NULL DEFAULT '0.00',
  `type_compte` varchar(20) NOT NULL,
  `decouvert_autorise` decimal(15,2) NOT NULL DEFAULT '0.00',
  `taux_interet` decimal(5,2) NOT NULL DEFAULT '0.00',
  `date_creation` date NOT NULL DEFAULT (curdate()),
  `id_client` int NOT NULL,
  `statut` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`id_compte`),
  UNIQUE KEY `numero` (`numero`),
  KEY `idx_compte_client` (`id_client`),
  CONSTRAINT `fk_compte_client` FOREIGN KEY (`id_client`) REFERENCES `client` (`id_client`),
  CONSTRAINT `chk_compte_courant` CHECK (((`type_compte` = _utf8mb4'COURANT') or (`decouvert_autorise` = 0))),
  CONSTRAINT `chk_compte_epargne` CHECK (((`type_compte` = _utf8mb4'EPARGNE') or (`taux_interet` = 0))),
  CONSTRAINT `chk_decouvert` CHECK ((`decouvert_autorise` >= 0)),
  CONSTRAINT `chk_solde` CHECK ((`solde` >= -(`decouvert_autorise`))),
  CONSTRAINT `chk_taux` CHECK ((`taux_interet` >= 0)),
  CONSTRAINT `chk_type_compte` CHECK ((`type_compte` in (_utf8mb4'COURANT',_utf8mb4'EPARGNE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- transaction_bancaire : history of operations.
--   DEPOT            : destination only     RETRAIT  : source only
--   VIREMENT         : source + destination (different accounts)
--   PAIEMENT_FACTURE : source only (bill payment)
-- ---------------------------------------------------------------------
CREATE TABLE `transaction_bancaire` (
  `id_transaction` int NOT NULL AUTO_INCREMENT,
  `type_transaction` enum('DEPOT','RETRAIT','VIREMENT','PAIEMENT_FACTURE') NOT NULL,
  `montant` decimal(15,2) NOT NULL,
  `date_transaction` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `description` varchar(255) DEFAULT NULL,
  `statut` enum('VALIDEE','REFUSEE') NOT NULL,
  `id_compte_source` int DEFAULT NULL,
  `id_compte_destination` int DEFAULT NULL,
  PRIMARY KEY (`id_transaction`),
  KEY `idx_source_date` (`id_compte_source`,`date_transaction`),
  KEY `idx_destination_date` (`id_compte_destination`,`date_transaction`),
  CONSTRAINT `fk_transaction_destination` FOREIGN KEY (`id_compte_destination`) REFERENCES `compte_bancaire` (`id_compte`),
  CONSTRAINT `fk_transaction_source` FOREIGN KEY (`id_compte_source`) REFERENCES `compte_bancaire` (`id_compte`),
  CONSTRAINT `chk_montant` CHECK ((`montant` > 0)),
  CONSTRAINT `chk_transaction_comptes` CHECK ((((`type_transaction` = _utf8mb4'DEPOT') and (`id_compte_source` is null) and (`id_compte_destination` is not null)) or ((`type_transaction` = _utf8mb4'RETRAIT') and (`id_compte_source` is not null) and (`id_compte_destination` is null)) or ((`type_transaction` = _utf8mb4'VIREMENT') and (`id_compte_source` is not null) and (`id_compte_destination` is not null) and (`id_compte_source` <> `id_compte_destination`)) or ((`type_transaction` = _utf8mb4'PAIEMENT_FACTURE') and (`id_compte_source` is not null) and (`id_compte_destination` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- bill : bill issued to a client. "Overdue" is not stored: it is an
-- UNPAID bill whose due_date is in the past.
-- ---------------------------------------------------------------------
CREATE TABLE `bill` (
  `id_bill` int NOT NULL AUTO_INCREMENT,
  `reference` varchar(40) NOT NULL,
  `provider` varchar(100) NOT NULL,
  `category` varchar(20) NOT NULL,
  `amount` decimal(15,2) NOT NULL,
  `issue_date` date NOT NULL,
  `due_date` date NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'UNPAID',
  `id_client` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `paid_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id_bill`),
  UNIQUE KEY `uk_bill_reference` (`reference`),
  KEY `idx_bill_client_status` (`id_client`,`status`),
  CONSTRAINT `fk_bill_client` FOREIGN KEY (`id_client`) REFERENCES `client` (`id_client`),
  CONSTRAINT `chk_bill_amount` CHECK ((`amount` > 0)),
  CONSTRAINT `chk_bill_category` CHECK ((`category` in (_utf8mb4'ELECTRICITY',_utf8mb4'WATER',_utf8mb4'INTERNET',_utf8mb4'TELEPHONE',_utf8mb4'OTHER'))),
  CONSTRAINT `chk_bill_dates` CHECK ((`due_date` >= `issue_date`)),
  CONSTRAINT `chk_bill_paid_at` CHECK (((`status` = _utf8mb4'PAID') = (`paid_at` is not null))),
  CONSTRAINT `chk_bill_status` CHECK ((`status` in (_utf8mb4'UNPAID',_utf8mb4'PAID',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- bill_payment : successful payment of a bill. UNIQUE(id_bill) makes a
-- double payment impossible; id_transaction links the debit operation.
-- ---------------------------------------------------------------------
CREATE TABLE `bill_payment` (
  `id_bill_payment` int NOT NULL AUTO_INCREMENT,
  `reference` varchar(40) NOT NULL,
  `id_bill` int NOT NULL,
  `id_compte` int NOT NULL,
  `id_transaction` int NOT NULL,
  `amount` decimal(15,2) NOT NULL,
  `payment_date` datetime NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'COMPLETED',
  PRIMARY KEY (`id_bill_payment`),
  UNIQUE KEY `uk_bill_payment_reference` (`reference`),
  UNIQUE KEY `uk_bill_payment_bill` (`id_bill`),
  UNIQUE KEY `uk_bill_payment_transaction` (`id_transaction`),
  KEY `idx_bill_payment_compte` (`id_compte`),
  CONSTRAINT `fk_bill_payment_bill` FOREIGN KEY (`id_bill`) REFERENCES `bill` (`id_bill`),
  CONSTRAINT `fk_bill_payment_compte` FOREIGN KEY (`id_compte`) REFERENCES `compte_bancaire` (`id_compte`),
  CONSTRAINT `fk_bill_payment_transaction` FOREIGN KEY (`id_transaction`) REFERENCES `transaction_bancaire` (`id_transaction`),
  CONSTRAINT `chk_bill_payment_amount` CHECK ((`amount` > 0)),
  CONSTRAINT `chk_bill_payment_status` CHECK ((`status` = _utf8mb4'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- password_reset_token / account_activation_token : single-use,
-- time-limited tokens. Only the SHA-256 hash of the token is stored.
-- ---------------------------------------------------------------------
CREATE TABLE `password_reset_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token_hash` varchar(64) NOT NULL,
  `id_utilisateur` int NOT NULL,
  `expiry_date` datetime NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token_hash` (`token_hash`),
  KEY `fk_reset_token_user` (`id_utilisateur`),
  CONSTRAINT `fk_reset_token_user` FOREIGN KEY (`id_utilisateur`) REFERENCES `utilisateur` (`id_utilisateur`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `account_activation_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token_hash` varchar(64) NOT NULL,
  `id_utilisateur` int NOT NULL,
  `expiry_date` datetime NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activation_token_hash` (`token_hash`),
  KEY `idx_activation_token_user` (`id_utilisateur`),
  CONSTRAINT `fk_activation_token_user` FOREIGN KEY (`id_utilisateur`) REFERENCES `utilisateur` (`id_utilisateur`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
