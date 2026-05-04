import { setActivePinia, createPinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useNodesStore } from '~/stores/nodes';
import type { NodesApi } from '~/composables/useNodesApi';
import type { CreateNodeRequest, NodeView } from '~/types/node';

const OWNER = '11111111-1111-1111-1111-111111111111';

function makeNode(overrides: Partial<NodeView> = {}): NodeView {
    return {
        id: '22222222-2222-2222-2222-222222222222',
        ownerId: OWNER,
        network: 'HOODI',
        executionLayer: 'BESU',
        consensusLayer: 'TEKU',
        status: 'READY',
        endpoint: 'http://localhost:8545',
        reason: null,
        ...overrides,
    };
}

function makeApi(overrides: Partial<NodesApi> = {}): NodesApi {
    return {
        list: vi.fn(),
        get: vi.fn(),
        create: vi.fn(),
        terminate: vi.fn(),
        ...overrides,
    };
}

describe('useNodesStore', () => {
    beforeEach(() => {
        setActivePinia(createPinia());
    });

    it('fetchList_should_replace_byId_when_api_returns_nodes', async () => {
        const node = makeNode();
        const api = makeApi({ list: vi.fn().mockResolvedValue([node]) });
        const store = useNodesStore();
        store.byId = {
            'stale-id': makeNode({ id: 'stale-id', status: 'TERMINATED' }),
        };

        await store.fetchList(api);

        expect(store.all).toHaveLength(1);
        expect(store.byId[node.id]).toEqual(node);
        expect(store.byId['stale-id']).toBeUndefined();
        expect(store.listLoading).toBe(false);
        expect(store.listError).toBeNull();
    });

    it('fetchList_should_set_listError_when_api_throws', async () => {
        const api = makeApi({
            list: vi.fn().mockRejectedValue(new Error('boom')),
        });
        const store = useNodesStore();

        await expect(store.fetchList(api)).rejects.toThrow('boom');
        expect(store.listError).toBe('boom');
        expect(store.listLoading).toBe(false);
    });

    it('fetchOne_should_upsert_node_when_api_returns_value', async () => {
        const node = makeNode();
        const api = makeApi({ get: vi.fn().mockResolvedValue(node) });
        const store = useNodesStore();

        const result = await store.fetchOne(api, node.id);

        expect(result).toEqual(node);
        expect(store.get(node.id)).toEqual(node);
    });

    it('create_should_optimistically_upsert_a_REQUESTED_node_when_accepted', async () => {
        const accepted = {
            id: '33333333-3333-3333-3333-333333333333',
            status: 'REQUESTED' as const,
        };
        const api = makeApi({
            create: vi.fn().mockResolvedValue(accepted),
        });
        const store = useNodesStore();
        const req: CreateNodeRequest = {
            ownerId: OWNER,
            network: 'SEPOLIA',
            executionLayer: 'BESU',
            consensusLayer: 'TEKU',
        };

        const result = await store.create(api, req);

        expect(result).toEqual(accepted);
        const stored = store.get(accepted.id);
        expect(stored).toBeDefined();
        expect(stored?.status).toBe('REQUESTED');
        expect(stored?.network).toBe('SEPOLIA');
        expect(stored?.endpoint).toBeNull();
    });

    it('terminate_should_mark_existing_node_TERMINATING_when_api_succeeds', async () => {
        const node = makeNode({ status: 'READY' });
        const api = makeApi({
            terminate: vi.fn().mockResolvedValue(undefined),
        });
        const store = useNodesStore();
        store.upsert(node);

        await store.terminate(api, node.id);

        expect(store.get(node.id)?.status).toBe('TERMINATING');
        expect(api.terminate).toHaveBeenCalledWith(node.id);
    });
});
