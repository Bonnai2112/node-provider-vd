CREATE TABLE validator_keys (
    id UUID PRIMARY KEY,
    node_id UUID NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
    pubkey VARCHAR(98) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_validator_keys_pubkey UNIQUE (pubkey)
);

CREATE INDEX idx_validator_keys_node_id ON validator_keys(node_id);
