<script setup lang="ts">
import {
    NETWORKS,
    type ClClient,
    type CreateNodeRequest,
    type ElClient,
    type Network,
} from '~/types/node';

const props = defineProps<{
    open: boolean;
    ownerId: string;
    submitting?: boolean;
    error?: string | null;
}>();

const emit = defineEmits<{
    submit: [payload: CreateNodeRequest];
    close: [];
}>();

const network = ref<Network>('HOODI');
// Seul couple supporté en v1 d'après le ticket: BESU + TEKU.
const executionLayer = ref<ElClient>('BESU');
const consensusLayer = ref<ClClient>('TEKU');

watch(
    () => props.open,
    (isOpen) => {
        if (isOpen) {
            network.value = 'HOODI';
            executionLayer.value = 'BESU';
            consensusLayer.value = 'TEKU';
        }
    },
);

function onSubmit() {
    emit('submit', {
        ownerId: props.ownerId,
        network: network.value,
        executionLayer: executionLayer.value,
        consensusLayer: consensusLayer.value,
    });
}
</script>

<template>
    <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="provision-title"
        @click.self="emit('close')"
    >
        <div
            class="w-full max-w-md rounded-xl bg-white p-6 shadow-lg ring-1 ring-slate-200"
        >
            <h2
                id="provision-title"
                class="text-lg font-semibold text-slate-900"
            >
                Provisionner un nœud
            </h2>
            <p class="mt-1 text-sm text-slate-500">
                Sélectionne le réseau et le couple de clients.
            </p>

            <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
                <div>
                    <label
                        for="provision-network"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Network
                    </label>
                    <select
                        id="provision-network"
                        v-model="network"
                        class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                    >
                        <option v-for="n in NETWORKS" :key="n" :value="n">
                            {{ n }}
                        </option>
                    </select>
                </div>

                <div>
                    <label
                        for="provision-client-pair"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Client pair
                    </label>
                    <select
                        id="provision-client-pair"
                        class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                        disabled
                    >
                        <option value="BESU+TEKU">BESU + TEKU</option>
                    </select>
                    <!-- Phase 1: BESU+TEKU est l'unique combo supporté. -->
                    <p class="mt-1 text-xs text-slate-500">
                        Seul couple supporté pour l'instant.
                    </p>
                </div>

                <p
                    v-if="error"
                    class="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-rose-200"
                    role="alert"
                >
                    {{ error }}
                </p>

                <div class="flex justify-end gap-2 pt-2">
                    <button
                        type="button"
                        class="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                        :disabled="submitting"
                        @click="emit('close')"
                    >
                        Annuler
                    </button>
                    <button
                        type="submit"
                        class="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
                        :disabled="submitting"
                    >
                        {{ submitting ? 'Création…' : 'Provisionner' }}
                    </button>
                </div>
            </form>
        </div>
    </div>
</template>
