-- Reference DDL for the guardrails-audit JDBC adapter (PostgreSQL).
-- Apply manually or through your migration tool (Flyway, Liquibase, ...).
CREATE TABLE IF NOT EXISTS mcp_audit_log (
  event_id    UUID PRIMARY KEY,
  agent_id    VARCHAR(255) NOT NULL,
  tool_name   VARCHAR(255) NOT NULL,
  occurred_at TIMESTAMPTZ  NOT NULL,
  emitted_by  VARCHAR(64)  NOT NULL,
  event_type  VARCHAR(32)  NOT NULL,
  detail      TEXT         NOT NULL
);
