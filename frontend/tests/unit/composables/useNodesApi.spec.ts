import { describe, expect, it, vi } from 'vitest';
import { createNodesApi, type FetchLike } from '~/composables/useNodesApi';
import { DEFAULT_NODE_OPTIONS, type NodeView, type ValidatorKey } from '~/types/node';

const OWNER = '21111111-1111-1111-1111-111111111111';

const sampleNode: NodeView = {
    id: '22222222-2222-2222-2222-222222222222',
    ownerId: OWNER,
    network: 'HOODI',
    executionLayer: 'BESU',
    consensusLayer: 'TEKU',
    status: 'READY',
    endpoint: 'http://localhost:8545',
    reason: null,
    options: DEFAULT_NODE_OPTIONS,
    elSync: null,
    clSync: null,
    peers: null,
    lastObservedAt: null,
};

const sampleKey: ValidatorKey = {
    id: '33333333-3333-3333-3333-333333333333',
    pubkey: '0xabc',
    importedAt: '2026-05-04T10:00:00Z',
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

        const body = {
            network: 'HOODI' as const,
            executionLayer: 'BESU' as const,
            consensusLayer: 'TEKU' as const,
            validator: true,
            mevBoost: false,
            feeRecipient: '0x0000000000000000000000000000000000000001',
            graffiti: null,
            mevMinBid: null,
            mevBuildFactor: null,
        };
        const result = await api.create(body);

        expect(result).toEqual(accepted);
        expect(fetcher).toHaveBeenCalledWith('/api/v1/nodes', {
            method: 'POST',
            headers: { 'X-Owner-Id': OWNER },
            body,
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

    it('restart_should_POST_restart_subresource_with_owner_header_when_called', async () => {
        const fetcher = fakeFetcher(undefined);
        const api = createNodesApi(fetcher, OWNER);

        await api.restart(sampleNode.id);

        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/restart`,
            {
                method: 'POST',
                headers: { 'X-Owner-Id': OWNER },
            },
        );
    });

    it('enableValidator_should_POST_validator_enable_with_body_when_called', async () => {
        const fetcher = fakeFetcher(undefined);
        const api = createNodesApi(fetcher, OWNER);
        const body = { feeRecipient: '0x' + '1'.repeat(40), graffiti: 'g' };

        await api.enableValidator(sampleNode.id, body);

        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator/enable`,
            {
                method: 'POST',
                headers: { 'X-Owner-Id': OWNER },
                body,
            },
        );
    });

    it('disableValidator_should_POST_validator_disable_with_owner_header_when_called', async () => {
        const fetcher = fakeFetcher(undefined);
        const api = createNodesApi(fetcher, OWNER);

        await api.disableValidator(sampleNode.id);

        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator/disable`,
            {
                method: 'POST',
                headers: { 'X-Owner-Id': OWNER },
            },
        );
    });

    it('enableMevBoost_should_POST_mev_boost_enable_with_body_when_called', async () => {
        const fetcher = fakeFetcher(undefined);
        const api = createNodesApi(fetcher, OWNER);
        const body = { mevMinBid: '0.1', mevBuildFactor: 80 };

        await api.enableMevBoost(sampleNode.id, body);

        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/mev-boost/enable`,
            {
                method: 'POST',
                headers: { 'X-Owner-Id': OWNER },
                body,
            },
        );
    });

    it('disableMevBoost_should_POST_mev_boost_disable_with_owner_header_when_called', async () => {
        const fetcher = fakeFetcher(undefined);
        const api = createNodesApi(fetcher, OWNER);

        await api.disableMevBoost(sampleNode.id);

        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/mev-boost/disable`,
            {
                method: 'POST',
                headers: { 'X-Owner-Id': OWNER },
            },
        );
    });

    it('listValidatorKeys_should_GET_keys_with_owner_header_when_called', async () => {
        const fetcher = fakeFetcher([sampleKey]);
        const api = createNodesApi(fetcher, OWNER);

        const result = await api.listValidatorKeys(sampleNode.id);

        expect(result).toEqual([sampleKey]);
        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator-keys`,
            { method: 'GET', headers: { 'X-Owner-Id': OWNER } },
        );
    });

    it('startGenerateValidatorKeys_should_POST_count_and_withdrawal_when_called', async () => {
        const accepted = { jobId: '44444444-4444-4444-4444-444444444444' };
        const fetcher = fakeFetcher(accepted);
        const api = createNodesApi(fetcher, OWNER);

        const out = await api.startGenerateValidatorKeys(sampleNode.id, {
            count: 2,
            withdrawalAddress: '0x' + 'a'.repeat(40),
        });

        expect(out).toEqual(accepted);
        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator-keys/generate`,
            {
                method: 'POST',
                headers: { 'X-Owner-Id': OWNER },
                body: { count: 2, withdrawalAddress: '0x' + 'a'.repeat(40) },
            },
        );
    });

    it('pollValidatorKeyGenerationJob_should_GET_status_when_called', async () => {
        const status = {
            status: 'SUCCEEDED' as const,
            result: {
                mnemonic: 'twelve words …',
                password: 'secret',
                keys: [sampleKey],
            },
        };
        const fetcher = fakeFetcher(status);
        const api = createNodesApi(fetcher, OWNER);
        const jobId = '44444444-4444-4444-4444-444444444444';

        const out = await api.pollValidatorKeyGenerationJob(sampleNode.id, jobId);

        expect(out).toEqual(status);
        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator-keys/generate-jobs/${jobId}`,
            { method: 'GET', headers: { 'X-Owner-Id': OWNER } },
        );
    });

    it('importValidatorKeys_should_POST_multipart_form_when_called', async () => {
        const fetcher = fakeFetcher([sampleKey]);
        const api = createNodesApi(fetcher, OWNER);
        const file = new File(['{}'], 'keystore-1.json', {
            type: 'application/json',
        });

        const out = await api.importValidatorKeys(sampleNode.id, [file], 'p4ss');

        expect(out).toEqual([sampleKey]);
        expect(fetcher).toHaveBeenCalledTimes(1);
        const [url, opts] = fetcher.mock.calls[0]!;
        expect(url).toBe(`/api/v1/nodes/${sampleNode.id}/validator-keys/import`);
        expect(opts.method).toBe('POST');
        expect(opts.headers).toEqual({ 'X-Owner-Id': OWNER });
        expect(opts.body).toBeInstanceOf(FormData);
        const form = opts.body as FormData;
        expect(form.get('password')).toBe('p4ss');
        const keystore = form.get('keystores');
        expect(keystore).toBeInstanceOf(File);
        expect((keystore as File).name).toBe('keystore-1.json');
    });

    it('downloadKeystores_should_GET_zip_blob_when_called', async () => {
        const blob = new Blob(['fake-zip'], { type: 'application/zip' });
        const fetcher = fakeFetcher(blob);
        const api = createNodesApi(fetcher, OWNER);

        const out = await api.downloadKeystores(sampleNode.id);

        expect(out).toBe(blob);
        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator-keys/download`,
            {
                method: 'GET',
                headers: { 'X-Owner-Id': OWNER },
                responseType: 'blob',
            },
        );
    });

    it('downloadDepositData_should_GET_json_blob_when_called', async () => {
        const blob = new Blob(['[]'], { type: 'application/json' });
        const fetcher = fakeFetcher(blob);
        const api = createNodesApi(fetcher, OWNER);

        const out = await api.downloadDepositData(sampleNode.id);

        expect(out).toBe(blob);
        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator-keys/deposit-data`,
            {
                method: 'GET',
                headers: { 'X-Owner-Id': OWNER },
                responseType: 'blob',
            },
        );
    });

    it('downloadKeystoreFor_should_GET_keystore_blob_for_pubkey_when_called', async () => {
        const blob = new Blob(['{}'], { type: 'application/json' });
        const fetcher = fakeFetcher(blob);
        const api = createNodesApi(fetcher, OWNER);
        const pubkey = '0xabc123';

        const out = await api.downloadKeystoreFor(sampleNode.id, pubkey);

        expect(out).toBe(blob);
        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator-keys/${encodeURIComponent(pubkey)}/keystore`,
            {
                method: 'GET',
                headers: { 'X-Owner-Id': OWNER },
                responseType: 'blob',
            },
        );
    });

    it('downloadDepositDataFor_should_GET_entry_blob_for_pubkey_when_called', async () => {
        const blob = new Blob(['[{}]'], { type: 'application/json' });
        const fetcher = fakeFetcher(blob);
        const api = createNodesApi(fetcher, OWNER);
        const pubkey = '0xdef456';

        const out = await api.downloadDepositDataFor(sampleNode.id, pubkey);

        expect(out).toBe(blob);
        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator-keys/${encodeURIComponent(pubkey)}/deposit-data`,
            {
                method: 'GET',
                headers: { 'X-Owner-Id': OWNER },
                responseType: 'blob',
            },
        );
    });

    it('generateTopupDepositData_should_POST_topup_request_for_pubkey_when_called', async () => {
        const blob = new Blob(['[{}]'], { type: 'application/json' });
        const fetcher = fakeFetcher(blob);
        const api = createNodesApi(fetcher, OWNER);
        const pubkey = '0xfeedbeef';
        const body = { amountEth: 32, keystorePassword: 'secret' };

        const out = await api.generateTopupDepositData(
            sampleNode.id,
            pubkey,
            body,
        );

        expect(out).toBe(blob);
        expect(fetcher).toHaveBeenCalledWith(
            `/api/v1/nodes/${sampleNode.id}/validator-keys/${encodeURIComponent(pubkey)}/topup-deposit-data`,
            {
                method: 'POST',
                headers: { 'X-Owner-Id': OWNER },
                body,
                responseType: 'blob',
            },
        );
    });
});
