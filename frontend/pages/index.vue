<script setup lang="ts">
import { useNodesStore } from '~/stores/nodes';
import type { CreateNodeRequest } from '~/types/node';

const config = useRuntimeConfig();
const ownerId = config.public.devOwnerId as string;

const api = useNodesApi();
const store = useNodesStore();
const router = useRouter();

const modalOpen = ref(false);

function openModal() {
    store.createError = null;
    modalOpen.value = true;
}

function closeModal() {
    if (store.creating) return;
    modalOpen.value = false;
}

async function onSubmit(payload: CreateNodeRequest) {
    try {
        const accepted = await store.create(api, payload);
        modalOpen.value = false;
        await router.push(`/nodes/${accepted.id}`);
    } catch {
        // erreur capturée par le store, affichée dans la modale
    }
}
</script>

<template>
    <section class="space-y-6">
        <div>
            <h1 class="text-3xl font-semibold tracking-tight">
                Node Provider
            </h1>
            <p class="mt-2 text-slate-600">
                Provisioning de nœuds Ethereum testnet (Hoodi, Sepolia) et
                faucet PoW.
            </p>
        </div>

        <div class="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <h2 class="text-lg font-semibold text-slate-900">Démarrer</h2>
            <p class="mt-1 text-sm text-slate-600">
                Crée un nœud testnet, suis sa progression, récupère son
                endpoint JSON-RPC.
            </p>
            <div class="mt-4 flex gap-3">
                <button
                    type="button"
                    class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
                    data-testid="provision-btn"
                    @click="openModal"
                >
                    Provisionner un nœud
                </button>
                <NuxtLink
                    to="/nodes"
                    class="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                    Voir mes nœuds
                </NuxtLink>
            </div>
        </div>

        <ProvisionNodeModal
            :open="modalOpen"
            :owner-id="ownerId"
            :submitting="store.creating"
            :error="store.createError"
            @submit="onSubmit"
            @close="closeModal"
        />
    </section>
</template>
