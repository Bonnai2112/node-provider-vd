ALTER TABLE nodes DROP CONSTRAINT chk_nodes_status_kind;

ALTER TABLE nodes ADD CONSTRAINT chk_nodes_status_kind CHECK (status_kind IN (
    'REQUESTED', 'PROVISIONING', 'SYNCING', 'READY',
    'DEGRADED', 'STOPPED', 'TERMINATING', 'TERMINATED', 'FAILED'
));
