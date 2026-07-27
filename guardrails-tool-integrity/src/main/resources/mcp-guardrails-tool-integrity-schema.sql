-- Reference DDL for the guardrails-tool-integrity JDBC adapter (PostgreSQL).
-- Apply manually or through your migration tool (Flyway, Liquibase, ...).
CREATE TABLE IF NOT EXISTS mcp_tool_baseline (
  tool_name      VARCHAR(255) PRIMARY KEY,
  fingerprint    CHAR(64)     NOT NULL,
  established_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
