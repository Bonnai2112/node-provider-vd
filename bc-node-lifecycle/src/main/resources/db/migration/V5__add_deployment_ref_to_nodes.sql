ALTER TABLE nodes
    ADD COLUMN deployment_ref UUID;

CREATE INDEX idx_nodes_deployment_ref ON nodes (deployment_ref);
