CREATE INDEX ix_audit_history
    ON dbo.audit_entry(recorded_at_utc DESC, id DESC)
    INCLUDE (actor_user_id, action, entity_type, entity_id);
