CREATE TABLE notifications (
    id                BIGSERIAL PRIMARY KEY,
    broker_account_id BIGINT NOT NULL REFERENCES broker_accounts(id) ON DELETE CASCADE,
    title             VARCHAR(200) NOT NULL,
    message           TEXT NOT NULL,
    type              VARCHAR(50) NOT NULL,
    is_read           BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_account ON notifications(broker_account_id);
CREATE INDEX idx_notifications_unread ON notifications(broker_account_id, is_read) WHERE is_read = false;
