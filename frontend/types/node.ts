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

export interface NodeOptions {
    validator: boolean;
    mevBoost: boolean;
    feeRecipient: string;
    graffiti: string | null;
    mevMinBid: string | null;
    mevBuildFactor: number | null;
}

export type SyncKind = 'SYNCED' | 'SYNCING' | 'NOT_SYNCING';

export interface ElSync {
    kind: SyncKind;
    currentBlock: number | null;
    highestBlock: number | null;
    percentage: number | null;
    blocksPerSecond: number | null;
    etaCompleteAt: string | null;
}

export interface ClSync {
    kind: SyncKind;
    headSlot: number | null;
    syncDistance: number | null;
    percentage: number | null;
    slotsPerSecond: number | null;
    etaCompleteAt: string | null;
}

export interface NodeView {
    id: string;
    ownerId: string;
    network: Network;
    executionLayer: ElClient;
    consensusLayer: ClClient;
    status: NodeStatusKind;
    endpoint: string | null;
    reason: string | null;
    options: NodeOptions;
    elSync: ElSync | null;
    clSync: ClSync | null;
    peers: number | null;
    lastObservedAt: string | null;
}

export interface CreateNodeRequest {
    network: Network;
    executionLayer: ElClient;
    consensusLayer: ClClient;
    validator: boolean;
    mevBoost: boolean;
    feeRecipient: string | null;
    graffiti: string | null;
    mevMinBid: string | null;
    mevBuildFactor: number | null;
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

export interface ValidatorKey {
    id: string;
    pubkey: string;
    importedAt: string;
}

export interface GenerateValidatorKeysRequest {
    count: number;
    withdrawalAddress: string;
}

export interface GenerateValidatorKeysResponse {
    mnemonic: string;
    password: string;
    keys: ValidatorKey[];
}

export const DEFAULT_NODE_OPTIONS: NodeOptions = {
    validator: false,
    mevBoost: false,
    feeRecipient: '',
    graffiti: null,
    mevMinBid: null,
    mevBuildFactor: null,
};
