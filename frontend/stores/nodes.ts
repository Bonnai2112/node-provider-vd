import { defineStore } from 'pinia';
import type {
    CreateNodeRequest,
    NodeView,
    ValidatorKey,
} from '~/types/node';
import { DEFAULT_NODE_OPTIONS } from '~/types/node';
import type { NodesApi } from '~/composables/useNodesApi';

interface State {
    byId: Record<string, NodeView>;
    keysByNode: Record<string, ValidatorKey[]>;
    listLoading: boolean;
    listError: string | null;
    detailLoading: boolean;
    detailError: string | null;
    creating: boolean;
    createError: string | null;
    keysLoading: boolean;
    keysError: string | null;
}

function errorMessage(e: unknown): string {
    if (e instanceof Error) return e.message;
    if (typeof e === 'string') return e;
    return 'Unknown error';
}

export const useNodesStore = defineStore('nodes', {
    state: (): State => ({
        byId: {},
        keysByNode: {},
        listLoading: false,
        listError: null,
        detailLoading: false,
        detailError: null,
        creating: false,
        createError: null,
        keysLoading: false,
        keysError: null,
    }),

    getters: {
        all: (state): NodeView[] => Object.values(state.byId),
        get:
            (state) =>
            (id: string): NodeView | undefined =>
                state.byId[id],
        keysFor:
            (state) =>
            (nodeId: string): ValidatorKey[] =>
                state.keysByNode[nodeId] ?? [],
    },

    actions: {
        upsert(node: NodeView) {
            this.byId[node.id] = node;
        },

        remove(id: string) {
            delete this.byId[id];
        },

        async fetchList(api: NodesApi) {
            this.listLoading = true;
            this.listError = null;
            try {
                const nodes = await api.list();
                const next: Record<string, NodeView> = {};
                for (const n of nodes) next[n.id] = n;
                this.byId = next;
            } catch (e) {
                this.listError = errorMessage(e);
                throw e;
            } finally {
                this.listLoading = false;
            }
        },

        async fetchOne(api: NodesApi, id: string) {
            this.detailLoading = true;
            this.detailError = null;
            try {
                const node = await api.get(id);
                this.upsert(node);
                return node;
            } catch (e) {
                this.detailError = errorMessage(e);
                throw e;
            } finally {
                this.detailLoading = false;
            }
        },

        async create(api: NodesApi, ownerId: string, req: CreateNodeRequest) {
            this.creating = true;
            this.createError = null;
            try {
                const accepted = await api.create(req);
                const optimistic: NodeView = {
                    id: accepted.id,
                    ownerId,
                    network: req.network,
                    executionLayer: req.executionLayer,
                    consensusLayer: req.consensusLayer,
                    status: accepted.status,
                    endpoint: null,
                    reason: null,
                    options: {
                        ...DEFAULT_NODE_OPTIONS,
                        validator: req.validator,
                        mevBoost: req.mevBoost,
                        feeRecipient:
                            req.feeRecipient ?? DEFAULT_NODE_OPTIONS.feeRecipient,
                        graffiti: req.graffiti,
                        mevMinBid: req.mevMinBid,
                        mevBuildFactor: req.mevBuildFactor,
                    },
                    elSync: null,
                    clSync: null,
                    peers: null,
                    lastObservedAt: null,
                };
                this.upsert(optimistic);
                return accepted;
            } catch (e) {
                this.createError = errorMessage(e);
                throw e;
            } finally {
                this.creating = false;
            }
        },

        async terminate(api: NodesApi, id: string) {
            await api.terminate(id);
            const existing = this.byId[id];
            if (existing) {
                this.byId[id] = { ...existing, status: 'TERMINATING' };
            }
        },

        async restart(api: NodesApi, id: string) {
            await api.restart(id);
            const existing = this.byId[id];
            if (existing) {
                // Mirror what the backend will do: STOPPED → PROVISIONING. The reconciler will
                // pick up SYNCING/READY transitions from there at the next poll tick.
                this.byId[id] = { ...existing, status: 'PROVISIONING' };
            }
        },

        async fetchValidatorKeys(api: NodesApi, nodeId: string) {
            this.keysLoading = true;
            this.keysError = null;
            try {
                const keys = await api.listValidatorKeys(nodeId);
                this.keysByNode[nodeId] = keys;
                return keys;
            } catch (e) {
                this.keysError = errorMessage(e);
                throw e;
            } finally {
                this.keysLoading = false;
            }
        },

        appendValidatorKeys(nodeId: string, keys: ValidatorKey[]) {
            const existing = this.keysByNode[nodeId] ?? [];
            const merged = [...existing];
            for (const k of keys) {
                if (!merged.some((m) => m.id === k.id)) merged.push(k);
            }
            this.keysByNode[nodeId] = merged;
        },
    },
});
