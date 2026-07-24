-- Reference DDL for the guardrails-ratelimit JDBC adapter (PostgreSQL).
-- Apply manually or through your migration tool (Flyway, Liquibase, ...).
-- Old windows are not cleaned automatically: schedule a periodic
--   DELETE FROM mcp_rate_limit_counter WHERE window_start < now() - interval '1 day';
CREATE TABLE IF NOT EXISTS mcp_rate_limit_counter (
  agent_id         VARCHAR(255) NOT NULL,
  tool_name        VARCHAR(255) NOT NULL,
  window_start     TIMESTAMPTZ  NOT NULL,
  invocation_count BIGINT       NOT NULL,
  PRIMARY KEY (agent_id, tool_name, window_start)
);
CREATE INDEX IF NOT EXISTS idx_mcp_rate_limit_window
  ON mcp_rate_limit_counter (window_start);
