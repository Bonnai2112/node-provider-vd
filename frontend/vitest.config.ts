import { defineConfig } from 'vitest/config';
import { fileURLToPath } from 'node:url';

export default defineConfig({
    resolve: {
        alias: {
            '~': fileURLToPath(new URL('./', import.meta.url)),
            '@': fileURLToPath(new URL('./', import.meta.url)),
        },
    },
    test: {
        environment: 'happy-dom',
        globals: true,
        include: ['tests/unit/**/*.spec.ts'],
        setupFiles: ['tests/unit/setup.ts'],
    },
});
