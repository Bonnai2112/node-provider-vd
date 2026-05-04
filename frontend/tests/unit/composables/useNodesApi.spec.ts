import { describe, expect, it, vi } from 'vitest';
import { createNodesApi, type FetchLike } from '~/composables/useNodesApi';
import type { NodeView } from '~/types/node';

const OWNER = '11111111-1111-1111-1111-111111111111';

const sampleNode: NodeView = {
    id: '22222222-2222-2222-2222-222222222222',
    ownerId: OWNER,
    network: 'HOODI',
    executionLayer: 'BESU',
    consensusLayer: 'TEKU',
    status: 'READY',
    endpoint: 'http://localhost:8545',
    reason: null,
};

function fakeFetcher(returnValue: unknown) {
    const fn = vi.fn(async () => returnValue);
    return fn as unknown as FetchLike & ReturnType<typeof vi.fn>;
}

describe('useNodesApi (createNodesApi)', () => {
    it('list_should_GET_nodes_with_owner_header_when_called', async () => {
        const fetcher = fakeFetcher([sampleNode]);
        const api = createNodesApi(fetcher, OWNER);

        const result = await api.list();

        expect(result).toEqual([sampleNode]);
        expect(fetcher).toHaveBeenCalledWith('/api/v1/nodes', {
            method: 'GET',
            headers: { 'X-Owner-Id': OWNER },
        });
    });

    it('get_should_GET_node_by_id_when_called', async () => {
        const fetcher = fakeFetcher(sampleNode);
        const api = createNodesApi(fetcher, OWNER);

        const result = await api.get(sampleNode.id);

        expect(result).toEqual(sampleNode);
        expect(fetcher).toHaveBeenCalledWith(`/api/v1/nodes/${sampleNode.id}`, {
            method: 'GET',
            headers: { 'X-Owner-Id': OWNER },
        });
    });

    it('create_should_POST_with_body_and_header_when_called', async () => {
        const accepted = { id: sampleNode.id, status: 'REQUESTED' as const };
        const fetcher = fakeFetcher(accepted);
        const api = createNodesApi(fetcher, OWNER);

        const result = await api.create({
            ownerId: OWNER,
            network: 'HOODI',
            executionLayer: 'BESU',
            consensusLayer: 'TEKU',
        });

        expect(result).toEqual(accepted);
        expect(fetcher).toHaveBeenCalledWith('/api/v1/nodes', {
            method: 'POST',
            headers: { 'X-Owner-Id': OWNER },
            body: {
                ownerId: OWNER,
                network: 'HOODI',
                executionLayer: 'BESU',
                consensusLayer: 'TEKU',
            },
        });
    });

    it('terminate_should_DELETE_node_by_id_when_called', async () => {
        const fetcher = fakeFetcher(undefined);
        const api = createNodesApi(fetcher, OWNER);

        await api.terminate(sampleNode.id);

        expect(fetcher).toHaveBeenCalledWith(`/api/v1/nodes/${sampleNode.id}`, {
            method: 'DELETE',
            headers: { 'X-Owner-Id': OWNER },
        });
    });
});
