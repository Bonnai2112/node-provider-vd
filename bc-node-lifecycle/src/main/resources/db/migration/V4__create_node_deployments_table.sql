CREATE TABLE node_deployments (
    id          UUID         PRIMARY KEY,
    node_id     UUID         NOT NULL REFERENCES nodes (id) ON DELETE CASCADE,
    payload     JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_node_deployments_node_id ON node_deployments (node_id);
