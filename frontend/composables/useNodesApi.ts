import type {
    CreateNodeRequest,
    GenerateTopupDepositRequest,
    GenerateValidatorKeysAcceptedResponse,
    GenerateValidatorKeysRequest,
    KeyGenerationJobStatusResponse,
    NodeAcceptedResponse,
    NodeView,
    ValidatorKey,
} from '~/types/node';

export interface EnableValidatorRequest {
    feeRecipient: string;
    graffiti: string | null;
}

export interface EnableMevBoostRequest {
    mevMinBid: string;
    mevBuildFactor: number;
}

export interface NodesApi {
    list(): Promise<NodeView[]>;
    get(id: string): Promise<NodeView>;
    create(req: CreateNodeRequest): Promise<NodeAcceptedResponse>;
    terminate(id: string): Promise<void>;
    restart(id: string): Promise<void>;
    enableValidator(id: string, req: EnableValidatorRequest): Promise<void>;
    disableValidator(id: string): Promise<void>;
    enableMevBoost(id: string, req: EnableMevBoostRequest): Promise<void>;
    disableMevBoost(id: string): Promise<void>;
    listValidatorKeys(nodeId: string): Promise<ValidatorKey[]>;
    startGenerateValidatorKeys(
        nodeId: string,
        req: GenerateValidatorKeysRequest,
    ): Promise<GenerateValidatorKeysAcceptedResponse>;
    pollValidatorKeyGenerationJob(
        nodeId: string,
        jobId: string,
    ): Promise<KeyGenerationJobStatusResponse>;
    importValidatorKeys(
        nodeId: string,
        keystores: File[],
        password: string,
    ): Promise<ValidatorKey[]>;
    downloadKeystores(nodeId: string): Promise<Blob>;
    downloadDepositData(nodeId: string): Promise<Blob>;
    downloadKeystoreFor(nodeId: string, pubkey: string): Promise<Blob>;
    downloadDepositDataFor(nodeId: string, pubkey: string): Promise<Blob>;
    generateTopupDepositData(
        nodeId: string,
        pubkey: string,
        req: GenerateTopupDepositRequest,
    ): Promise<Blob>;
}

export interface FetchLike {
    <T>(url: string, opts: Record<string, unknown>): Promise<T>;
}

export function createNodesApi(fetcher: FetchLike, ownerId: string): NodesApi {
    const headers = { 'X-Owner-Id': ownerId };

    return {
        list: () =>
            fetcher<NodeView[]>('/api/v1/nodes', {
                method: 'GET',
                headers,
            }),

        get: (id) =>
            fetcher<NodeView>(`/api/v1/nodes/${id}`, {
                method: 'GET',
                headers,
            }),

        create: (req) =>
            fetcher<NodeAcceptedResponse>('/api/v1/nodes', {
                method: 'POST',
                headers,
                body: req,
            }),

        terminate: (id) =>
            fetcher<void>(`/api/v1/nodes/${id}`, {
                method: 'DELETE',
                headers,
            }),

        restart: (id) =>
            fetcher<void>(`/api/v1/nodes/${id}/restart`, {
                method: 'POST',
                headers,
            }),

        enableValidator: (id, req) =>
            fetcher<void>(`/api/v1/nodes/${id}/validator/enable`, {
                method: 'POST',
                headers,
                body: req,
            }),

        disableValidator: (id) =>
            fetcher<void>(`/api/v1/nodes/${id}/validator/disable`, {
                method: 'POST',
                headers,
            }),

        enableMevBoost: (id, req) =>
            fetcher<void>(`/api/v1/nodes/${id}/mev-boost/enable`, {
                method: 'POST',
                headers,
                body: req,
            }),

        disableMevBoost: (id) =>
            fetcher<void>(`/api/v1/nodes/${id}/mev-boost/disable`, {
                method: 'POST',
                headers,
            }),

        listValidatorKeys: (nodeId) =>
            fetcher<ValidatorKey[]>(`/api/v1/nodes/${nodeId}/validator-keys`, {
                method: 'GET',
                headers,
            }),

        startGenerateValidatorKeys: (nodeId, req) =>
            fetcher<GenerateValidatorKeysAcceptedResponse>(
                `/api/v1/nodes/${nodeId}/validator-keys/generate`,
                {
                    method: 'POST',
                    headers,
                    body: req,
                },
            ),

        pollValidatorKeyGenerationJob: (nodeId, jobId) =>
            fetcher<KeyGenerationJobStatusResponse>(
                `/api/v1/nodes/${nodeId}/validator-keys/generate-jobs/${jobId}`,
                {
                    method: 'GET',
                    headers,
                },
            ),

        importValidatorKeys: (nodeId, keystores, password) => {
            const form = new FormData();
            for (const f of keystores) form.append('keystores', f, f.name);
            form.append('password', password);
            return fetcher<ValidatorKey[]>(
                `/api/v1/nodes/${nodeId}/validator-keys/import`,
                {
                    method: 'POST',
                    headers,
                    body: form,
                },
            );
        },

        downloadKeystores: (nodeId) =>
            fetcher<Blob>(`/api/v1/nodes/${nodeId}/validator-keys/download`, {
                method: 'GET',
                headers,
                responseType: 'blob',
            }),

        downloadDepositData: (nodeId) =>
            fetcher<Blob>(`/api/v1/nodes/${nodeId}/validator-keys/deposit-data`, {
                method: 'GET',
                headers,
                responseType: 'blob',
            }),

        downloadKeystoreFor: (nodeId, pubkey) =>
            fetcher<Blob>(
                `/api/v1/nodes/${nodeId}/validator-keys/${encodeURIComponent(pubkey)}/keystore`,
                {
                    method: 'GET',
                    headers,
                    responseType: 'blob',
                },
            ),

        downloadDepositDataFor: (nodeId, pubkey) =>
            fetcher<Blob>(
                `/api/v1/nodes/${nodeId}/validator-keys/${encodeURIComponent(pubkey)}/deposit-data`,
                {
                    method: 'GET',
                    headers,
                    responseType: 'blob',
                },
            ),

        generateTopupDepositData: (nodeId, pubkey, req) =>
            fetcher<Blob>(
                `/api/v1/nodes/${nodeId}/validator-keys/${encodeURIComponent(pubkey)}/topup-deposit-data`,
                {
                    method: 'POST',
                    headers,
                    body: req,
                    responseType: 'blob',
                },
            ),
    };
}

export function useNodesApi(): NodesApi {
    const config = useRuntimeConfig();
    const fetcher = $fetch.create({
        baseURL: config.public.apiBase as string,
    }) as unknown as FetchLike;
    return createNodesApi(fetcher, config.public.devOwnerId as string);
}
