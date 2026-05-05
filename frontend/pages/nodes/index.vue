<script setup lang="ts">
import { useNodesStore } from '~/stores/nodes';

const api = useNodesApi();
const store = useNodesStore();

await useAsyncData('nodes-list', () => store.fetchList(api));

async function refresh() {
    await store.fetchList(api);
}
</script>

<template>
    <section class="space-y-6">
        <div class="flex items-end justify-between">
            <div>
                <h1 class="text-2xl font-semibold tracking-tight">
                    Mes nœuds
                </h1>
                <p class="mt-1 text-sm text-slate-600">
                    Liste des nœuds provisionnés pour l'utilisateur courant.
                </p>
            </div>
            <button
                type="button"
                class="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                :disabled="store.listLoading"
                @click="refresh"
            >
                {{ store.listLoading ? 'Chargement…' : 'Rafraîchir' }}
            </button>
        </div>

        <div
            v-if="store.listError"
            class="rounded-md bg-rose-50 px-4 py-3 text-sm text-rose-700 ring-1 ring-rose-200"
            role="alert"
        >
            Erreur de chargement : {{ store.listError }}
        </div>

        <div
            v-else-if="store.all.length === 0 && !store.listLoading"
            class="rounded-xl bg-white p-8 text-center shadow-sm ring-1 ring-slate-200"
        >
            <p class="text-sm text-slate-600">
                Aucun nœud pour l'instant.
            </p>
            <NuxtLink
                to="/"
                class="mt-3 inline-block text-sm font-medium text-slate-900 underline"
            >
                Provisionner un nœud
            </NuxtLink>
        </div>

        <ul v-else class="divide-y divide-slate-200 rounded-xl bg-white shadow-sm ring-1 ring-slate-200">
            <li
                v-for="node in store.all"
                :key="node.id"
                class="flex items-center justify-between px-4 py-3"
            >
                <NuxtLink
                    :to="`/nodes/${node.id}`"
                    class="flex flex-1 items-center gap-4"
                >
                    <span class="font-mono text-sm text-slate-700">
                        {{ node.id.slice(0, 8) }}…
                    </span>
                    <span class="text-sm text-slate-600">
                        {{ node.network }} · {{ node.executionLayer }}+{{
                            node.consensusLayer
                        }}
                    </span>
                </NuxtLink>
                <div class="flex items-center gap-2">
                    <span
                        v-if="node.options.validator"
                        class="inline-flex items-center rounded-full bg-violet-100 px-2 py-0.5 text-[10px] font-medium text-violet-800 ring-1 ring-inset ring-violet-200"
                    >
                        Validator
                    </span>
                    <span
                        v-if="node.options.mevBoost"
                        class="inline-flex items-center rounded-full bg-fuchsia-100 px-2 py-0.5 text-[10px] font-medium text-fuchsia-800 ring-1 ring-inset ring-fuchsia-200"
                    >
                        MEV
                    </span>
                    <NodeStatusBadge :status="node.status" />
                </div>
            </li>
        </ul>
    </section>
</template>
