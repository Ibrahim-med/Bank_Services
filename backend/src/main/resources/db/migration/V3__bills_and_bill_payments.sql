-- =====================================================================
-- V3 - Bill management and bill payment
--
-- 1. transaction_bancaire: new transaction type PAIEMENT_FACTURE
--    (bill payment = debit of a source account, no destination).
--    The ENUM is widened and chk_transaction_comptes is replaced, in ONE
--    ALTER statement, by a constraint that keeps the three existing rules
--    unchanged and adds the PAIEMENT_FACTURE rule. No row is modified.
-- 2. New tables: bill, bill_payment.
-- =====================================================================

ALTER TABLE `transaction_bancaire`
  MODIFY COLUMN `type_transaction`
      enum('DEPOT','RETRAIT','VIREMENT','PAIEMENT_FACTURE') NOT NULL,
  DROP CHECK `chk_transaction_comptes`,
  ADD CONSTRAINT `chk_transaction_comptes` CHECK (
        ((`type_transaction` = 'DEPOT')
            and (`id_compte_source` is null) and (`id_compte_destination` is not null))
     or ((`type_transaction` = 'RETRAIT')
            and (`id_compte_source` is not null) and (`id_compte_destination` is null))
     or ((`type_transaction` = 'VIREMENT')
            and (`id_compte_source` is not null) and (`id_compte_destination` is not null)
            and (`id_compte_source` <> `id_compte_destination`))
     or ((`type_transaction` = 'PAIEMENT_FACTURE')
            and (`id_compte_source` is not null) and (`id_compte_destination` is null))
  );

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
  KEY `idx_bill_client_status` (`id_client`, `status`),
  CONSTRAINT `fk_bill_client` FOREIGN KEY (`id_client`) REFERENCES `client` (`id_client`),
  CONSTRAINT `chk_bill_amount` CHECK (`amount` > 0),
  CONSTRAINT `chk_bill_dates` CHECK (`due_date` >= `issue_date`),
  CONSTRAINT `chk_bill_status` CHECK (`status` in ('UNPAID','PAID','CANCELLED')),
  CONSTRAINT `chk_bill_category`
      CHECK (`category` in ('ELECTRICITY','WATER','INTERNET','TELEPHONE','OTHER')),
  CONSTRAINT `chk_bill_paid_at` CHECK ((`status` = 'PAID') = (`paid_at` is not null))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
  -- A bill can be paid at most once (last line of defence against double payment).
  UNIQUE KEY `uk_bill_payment_bill` (`id_bill`),
  UNIQUE KEY `uk_bill_payment_transaction` (`id_transaction`),
  KEY `idx_bill_payment_compte` (`id_compte`),
  CONSTRAINT `fk_bill_payment_bill` FOREIGN KEY (`id_bill`) REFERENCES `bill` (`id_bill`),
  CONSTRAINT `fk_bill_payment_compte` FOREIGN KEY (`id_compte`) REFERENCES `compte_bancaire` (`id_compte`),
  CONSTRAINT `fk_bill_payment_transaction` FOREIGN KEY (`id_transaction`)
      REFERENCES `transaction_bancaire` (`id_transaction`),
  CONSTRAINT `chk_bill_payment_amount` CHECK (`amount` > 0),
  CONSTRAINT `chk_bill_payment_status` CHECK (`status` in ('COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
