<script setup lang="ts"></script>

<template>
    <header class="border-b border-slate-200 bg-white">
        <nav
            class="mx-auto flex max-w-5xl items-center justify-between px-6 py-4"
        >
            <NuxtLink
                to="/"
                class="text-base font-semibold tracking-tight text-slate-900"
            >
                Node Provider
            </NuxtLink>
            <ul class="flex items-center gap-4 text-sm">
                <li>
                    <NuxtLink
                        to="/"
                        class="text-slate-700 hover:text-slate-900"
                        active-class="text-slate-900 font-medium"
                    >
                        Accueil
                    </NuxtLink>
                </li>
                <li>
                    <NuxtLink
                        to="/nodes"
                        class="text-slate-700 hover:text-slate-900"
                        active-class="text-slate-900 font-medium"
                    >
                        Nœuds
                    </NuxtLink>
                </li>
            </ul>
        </nav>
    </header>
</template>
