CREATE INDEX idx_recurrence_events_account_period
    ON recurrence_events (account_id, recurrence_start_date, recurrence_end_date);
