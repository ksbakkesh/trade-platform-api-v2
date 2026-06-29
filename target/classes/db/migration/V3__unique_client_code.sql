ALTER TABLE broker_accounts DROP CONSTRAINT IF EXISTS uq_broker_client_code;
ALTER TABLE broker_accounts ADD CONSTRAINT uq_broker_client_code UNIQUE (client_code);
