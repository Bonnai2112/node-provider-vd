import { vi } from 'vitest';

vi.stubGlobal('useRuntimeConfig', () => ({
    public: {
        apiBase: 'http://localhost:8080',
        devOwnerId: '11111111-1111-1111-1111-111111111111',
    },
}));
