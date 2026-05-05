import { vi } from 'vitest';

vi.stubGlobal('useRuntimeConfig', () => ({
    public: {
        apiBase: 'http://localhost:8080',
        devOwnerId: '21111111-1111-1111-1111-111111111111',
    },
}));
