ALTER TABLE nodes DROP CONSTRAINT chk_nodes_el_client;
ALTER TABLE nodes ADD CONSTRAINT chk_nodes_el_client
    CHECK (el_client IN ('BESU', 'GETH', 'NETHERMIND', 'OPENETHEREUM', 'PARITY', 'ERIGON'));

ALTER TABLE nodes DROP CONSTRAINT chk_nodes_cl_client;
ALTER TABLE nodes ADD CONSTRAINT chk_nodes_cl_client
    CHECK (cl_client IN ('TEKU', 'LIGHTHOUSE', 'PRYSM', 'NIMBUS', 'LODESTAR', 'SIGMA', 'ANVIL'));
