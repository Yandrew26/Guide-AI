-- MySQL 8+. Creates everything the services expect. Safe to re-run.
CREATE DATABASE IF NOT EXISTS multi_agent_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE multi_agent_db;

-- Readers (search service: /user/*)
CREATE TABLE IF NOT EXISTS system_user (
  id           BIGINT PRIMARY KEY,
  user_id      VARCHAR(64)  NOT NULL UNIQUE,
  user_name    VARCHAR(255) NOT NULL,
  email        VARCHAR(255),
  mobile       VARCHAR(64),
  created_date DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Every question and answer (search service: /history/*)
CREATE TABLE IF NOT EXISTS rag_chat_history (
  id              BIGINT PRIMARY KEY,
  app_id          VARCHAR(64),
  user_id         VARCHAR(64)  NOT NULL,
  user_name       VARCHAR(255),
  conversation_id VARCHAR(256) NOT NULL,
  trace_id        VARCHAR(64)  NOT NULL,
  content         LONGTEXT,
  type            VARCHAR(16)  NOT NULL,
  created_date    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_history_conversation (conversation_id, created_date),
  INDEX idx_history_user (user_id, created_date)
);

-- Short-term memory window fed back to the model (SelfMysqlChatMemoryRepository)
CREATE TABLE IF NOT EXISTS rag_chat_memory (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id         VARCHAR(255) NOT NULL,
  conversation_id VARCHAR(256) NOT NULL,
  content         LONGTEXT     NOT NULL,
  type            VARCHAR(100) NOT NULL,
  timestamp       TIMESTAMP    NOT NULL,
  INDEX idx_memory_conversation (conversation_id, timestamp),
  CONSTRAINT chk_message_type_kb CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);

-- API clients of the open gateway (auth service: /auth/verify)
CREATE TABLE IF NOT EXISTS rag_auth_base (
  id             BIGINT PRIMARY KEY,
  auth_name      VARCHAR(255) NOT NULL,
  auth_id        VARCHAR(64)  NOT NULL UNIQUE,
  secret_key     VARCHAR(255) NOT NULL,
  enabled_status BOOLEAN      NOT NULL DEFAULT TRUE,
  created_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_date   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
