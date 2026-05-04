DROP INDEX IF EXISTS idx_nodes_deployment_ref;

ALTER TABLE nodes
    DROP COLUMN deployment_ref;

ALTER TABLE nodes
    ADD COLUMN deployment_ref JSONB;

DROP TABLE IF EXISTS node_deployments;
