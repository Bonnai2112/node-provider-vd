import { defineStore } from 'pinia';
import type { CreateNodeRequest, NodeView } from '~/types/node';
import type { NodesApi } from '~/composables/useNodesApi';

interface State {
    byId: Record<string, NodeView>;
    listLoading: boolean;
    listError: string | null;
    detailLoading: boolean;
    detailError: string | null;
    creating: boolean;
    createError: string | null;
}

function errorMessage(e: unknown): string {
    if (e instanceof Error) return e.message;
    if (typeof e === 'string') return e;
    return 'Unknown error';
}

export const useNodesStore = defineStore('nodes', {
    state: (): State => ({
        byId: {},
        listLoading: false,
        listError: null,
        detailLoading: false,
        detailError: null,
        creating: false,
        createError: null,
    }),

    getters: {
        all: (state): NodeView[] => Object.values(state.byId),
        get:
            (state) =>
            (id: string): NodeView | undefined =>
                state.byId[id],
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

        async create(api: NodesApi, req: CreateNodeRequest) {
            this.creating = true;
            this.createError = null;
            try {
                const accepted = await api.create(req);
                const optimistic: NodeView = {
                    id: accepted.id,
                    ownerId: req.ownerId,
                    network: req.network,
                    executionLayer: req.executionLayer,
                    consensusLayer: req.consensusLayer,
                    status: accepted.status,
                    endpoint: null,
                    reason: null,
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
    },
});
