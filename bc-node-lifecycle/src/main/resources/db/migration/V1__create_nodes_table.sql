CREATE TABLE nodes (
    id              UUID PRIMARY KEY,
    owner_id        UUID         NOT NULL,
    network         VARCHAR(20)  NOT NULL,
    el_client       VARCHAR(20)  NOT NULL,
    cl_client       VARCHAR(20)  NOT NULL,
    status_kind     VARCHAR(30)  NOT NULL,
    endpoint_uri    TEXT,
    status_reason   TEXT,
    CONSTRAINT chk_nodes_network   CHECK (network   IN ('HOODI', 'SEPOLIA')),
    CONSTRAINT chk_nodes_el_client CHECK (el_client IN ('BESU')),
    CONSTRAINT chk_nodes_cl_client CHECK (cl_client IN ('TEKU')),
    CONSTRAINT chk_nodes_status_kind CHECK (status_kind IN (
        'REQUESTED', 'PROVISIONING', 'SYNCING', 'READY',
        'DEGRADED', 'TERMINATING', 'TERMINATED', 'FAILED'
    ))
);

CREATE INDEX idx_nodes_owner_id ON nodes (owner_id);
