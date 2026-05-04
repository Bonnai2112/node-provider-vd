import type {
    CreateNodeRequest,
    NodeAcceptedResponse,
    NodeView,
} from '~/types/node';

export interface NodesApi {
    list(): Promise<NodeView[]>;
    get(id: string): Promise<NodeView>;
    create(req: CreateNodeRequest): Promise<NodeAcceptedResponse>;
    terminate(id: string): Promise<void>;
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
    };
}

export function useNodesApi(): NodesApi {
    const config = useRuntimeConfig();
    const fetcher = $fetch.create({
        baseURL: config.public.apiBase as string,
    }) as unknown as FetchLike;
    return createNodesApi(fetcher, config.public.devOwnerId as string);
}
