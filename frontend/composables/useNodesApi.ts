import type {
    CreateNodeRequest,
    GenerateValidatorKeysRequest,
    GenerateValidatorKeysResponse,
    NodeAcceptedResponse,
    NodeView,
    ValidatorKey,
} from '~/types/node';

export interface NodesApi {
    list(): Promise<NodeView[]>;
    get(id: string): Promise<NodeView>;
    create(req: CreateNodeRequest): Promise<NodeAcceptedResponse>;
    terminate(id: string): Promise<void>;
    listValidatorKeys(nodeId: string): Promise<ValidatorKey[]>;
    generateValidatorKeys(
        nodeId: string,
        req: GenerateValidatorKeysRequest,
    ): Promise<GenerateValidatorKeysResponse>;
    importValidatorKeys(
        nodeId: string,
        keystores: File[],
        password: string,
    ): Promise<ValidatorKey[]>;
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

        listValidatorKeys: (nodeId) =>
            fetcher<ValidatorKey[]>(`/api/v1/nodes/${nodeId}/validator-keys`, {
                method: 'GET',
                headers,
            }),

        generateValidatorKeys: (nodeId, req) =>
            fetcher<GenerateValidatorKeysResponse>(
                `/api/v1/nodes/${nodeId}/validator-keys/generate`,
                {
                    method: 'POST',
                    headers,
                    body: req,
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
    };
}

export function useNodesApi(): NodesApi {
    const config = useRuntimeConfig();
    const fetcher = $fetch.create({
        baseURL: config.public.apiBase as string,
    }) as unknown as FetchLike;
    return createNodesApi(fetcher, config.public.devOwnerId as string);
}
