-- =====================================================================
-- V1 - Baseline schema (structure of the existing BankServices database
-- as of 2026-11-19, exported with mysqldump --no-data).
--
-- An EXISTING database is marked as version 1 by Flyway
-- (spring.flyway.baseline-on-migrate=true), so this script is NEVER
-- executed against it. It only runs to build a brand-new, empty database.
-- =====================================================================

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

CREATE TABLE `utilisateur` (
  `id_utilisateur` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` varchar(20) NOT NULL,
  `id_client` int DEFAULT NULL,
  PRIMARY KEY (`id_utilisateur`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `id_client` (`id_client`),
  CONSTRAINT `fk_utilisateur_client` FOREIGN KEY (`id_client`) REFERENCES `client` (`id_client`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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

CREATE TABLE `transaction_bancaire` (
  `id_transaction` int NOT NULL AUTO_INCREMENT,
  `type_transaction` enum('DEPOT','RETRAIT','VIREMENT') NOT NULL,
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
  CONSTRAINT `chk_transaction_comptes` CHECK ((((`type_transaction` = _utf8mb4'DEPOT') and (`id_compte_source` is null) and (`id_compte_destination` is not null)) or ((`type_transaction` = _utf8mb4'RETRAIT') and (`id_compte_source` is not null) and (`id_compte_destination` is null)) or ((`type_transaction` = _utf8mb4'VIREMENT') and (`id_compte_source` is not null) and (`id_compte_destination` is not null) and (`id_compte_source` <> `id_compte_destination`))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
