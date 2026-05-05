// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
    compatibilityDate: '2026-01-01',
    devtools: { enabled: true },
    modules: ['@nuxtjs/tailwindcss', '@pinia/nuxt'],
    typescript: {
        strict: true,
        typeCheck: false,
    },
    css: ['~/assets/css/tailwind.css'],
    runtimeConfig: {
        public: {
            apiBase: '',
            devOwnerId: '21111111-1111-1111-1111-111111111111',
        },
    },
    nitro: {
        devProxy: {
            '/api': {
                target: 'http://localhost:8080/api',
                changeOrigin: true,
            },
        },
    },
});
