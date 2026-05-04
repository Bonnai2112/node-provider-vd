export const NETWORKS = ['HOODI', 'SEPOLIA'] as const;
export type Network = (typeof NETWORKS)[number];

export const EL_CLIENTS = ['BESU', 'GETH', 'NETHERMIND', 'ERIGON'] as const;
export type ElClient = (typeof EL_CLIENTS)[number];

export const CL_CLIENTS = ['TEKU', 'LIGHTHOUSE', 'PRYSM', 'NIMBUS', 'LODESTAR'] as const;
export type ClClient = (typeof CL_CLIENTS)[number];

export const NODE_STATUSES = [
    'REQUESTED',
    'PROVISIONING',
    'SYNCING',
    'READY',
    'DEGRADED',
    'TERMINATING',
    'TERMINATED',
    'FAILED',
] as const;
export type NodeStatusKind = (typeof NODE_STATUSES)[number];

export interface NodeView {
    id: string;
    ownerId: string;
    network: Network;
    executionLayer: ElClient;
    consensusLayer: ClClient;
    status: NodeStatusKind;
    endpoint: string | null;
    reason: string | null;
}

export interface CreateNodeRequest {
    ownerId: string;
    network: Network;
    executionLayer: ElClient;
    consensusLayer: ClClient;
}

export interface NodeAcceptedResponse {
    id: string;
    status: NodeStatusKind;
}

export const TERMINAL_STATUSES: ReadonlySet<NodeStatusKind> = new Set([
    'READY',
    'TERMINATED',
    'FAILED',
]);
