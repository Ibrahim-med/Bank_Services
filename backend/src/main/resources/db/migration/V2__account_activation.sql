-- =====================================================================
-- V2 - Client registration and account activation
--
-- Non-destructive:
--   * adds utilisateur.statut; every EXISTING user receives 'ACTIVE'
--     (the column default), so current logins keep working.
--   * creates account_activation_token (stores only SHA-256 hashes).
-- =====================================================================

ALTER TABLE `utilisateur`
  ADD COLUMN `statut` varchar(20) NOT NULL DEFAULT 'ACTIVE' AFTER `role`,
  ADD CONSTRAINT `chk_utilisateur_statut`
      CHECK (`statut` in ('PENDING_ACTIVATION','ACTIVE','DISABLED'));

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
  CONSTRAINT `fk_activation_token_user` FOREIGN KEY (`id_utilisateur`)
      REFERENCES `utilisateur` (`id_utilisateur`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
