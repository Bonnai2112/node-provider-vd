import type { Config } from 'tailwindcss';

export default <Partial<Config>>{
    content: [
        './components/**/*.{vue,js,ts}',
        './layouts/**/*.vue',
        './pages/**/*.vue',
        './composables/**/*.{js,ts}',
        './plugins/**/*.{js,ts}',
        './app.vue',
        './error.vue',
    ],
    theme: {
        extend: {},
    },
    plugins: [],
};
