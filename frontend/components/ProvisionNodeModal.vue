<script setup lang="ts">
import {
    CL_CLIENTS,
    EL_CLIENTS,
    NETWORKS,
    type ClClient,
    type CreateNodeRequest,
    type ElClient,
    type Network,
} from '~/types/node';

const props = defineProps<{
    open: boolean;
    submitting?: boolean;
    error?: string | null;
}>();

const emit = defineEmits<{
    submit: [payload: CreateNodeRequest];
    close: [];
}>();

const network = ref<Network>('HOODI');
const executionLayer = ref<ElClient>('GETH');
const consensusLayer = ref<ClClient>('LIGHTHOUSE');
const validator = ref(false);
const mevBoost = ref(false);
const feeRecipient = ref('');
const graffiti = ref('');
const mevMinBid = ref('');
const mevBuildFactor = ref<number | null>(null);
const showAdvanced = ref(false);

watch(
    () => props.open,
    (isOpen) => {
        if (isOpen) {
            network.value = 'HOODI';
            executionLayer.value = 'GETH';
            consensusLayer.value = 'LIGHTHOUSE';
            validator.value = false;
            mevBoost.value = false;
            feeRecipient.value = '';
            graffiti.value = '';
            mevMinBid.value = '';
            mevBuildFactor.value = null;
            showAdvanced.value = false;
        }
    },
);

function nullIfBlank(s: string): string | null {
    const trimmed = s.trim();
    return trimmed === '' ? null : trimmed;
}

function onSubmit() {
    emit('submit', {
        network: network.value,
        executionLayer: executionLayer.value,
        consensusLayer: consensusLayer.value,
        validator: validator.value,
        mevBoost: mevBoost.value,
        feeRecipient: nullIfBlank(feeRecipient.value),
        graffiti: nullIfBlank(graffiti.value),
        mevMinBid: nullIfBlank(mevMinBid.value),
        mevBuildFactor: mevBuildFactor.value,
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
            class="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-xl bg-white p-6 shadow-lg ring-1 ring-slate-200"
        >
            <h2
                id="provision-title"
                class="text-lg font-semibold text-slate-900"
            >
                Provisionner un nœud
            </h2>
            <p class="mt-1 text-sm text-slate-500">
                Sélectionne le réseau, le couple de clients et active le
                staking si besoin.
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

                <div class="grid grid-cols-2 gap-3">
                    <div>
                        <label
                            for="provision-el"
                            class="block text-sm font-medium text-slate-700"
                        >
                            Execution layer
                        </label>
                        <select
                            id="provision-el"
                            v-model="executionLayer"
                            class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                        >
                            <option v-for="el in EL_CLIENTS" :key="el" :value="el">
                                {{ el }}
                            </option>
                        </select>
                    </div>
                    <div>
                        <label
                            for="provision-cl"
                            class="block text-sm font-medium text-slate-700"
                        >
                            Consensus layer
                        </label>
                        <select
                            id="provision-cl"
                            v-model="consensusLayer"
                            class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                        >
                            <option v-for="cl in CL_CLIENTS" :key="cl" :value="cl">
                                {{ cl }}
                            </option>
                        </select>
                    </div>
                </div>

                <div class="space-y-2 rounded-md border border-slate-200 bg-slate-50 p-3">
                    <label class="flex items-center gap-2 text-sm font-medium text-slate-700">
                        <input
                            v-model="validator"
                            type="checkbox"
                            class="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-500"
                            data-testid="provision-validator"
                        />
                        Activer le staking (validator client)
                    </label>
                    <label class="flex items-center gap-2 text-sm font-medium text-slate-700">
                        <input
                            v-model="mevBoost"
                            type="checkbox"
                            class="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-500"
                            data-testid="provision-mevboost"
                        />
                        MEV-Boost
                    </label>
                </div>

                <button
                    type="button"
                    class="text-sm font-medium text-slate-600 hover:text-slate-900"
                    @click="showAdvanced = !showAdvanced"
                >
                    {{ showAdvanced ? '− Masquer' : '+ Options avancées' }}
                </button>

                <div v-if="showAdvanced" class="space-y-3 border-t border-slate-200 pt-3">
                    <div>
                        <label
                            for="provision-fee"
                            class="block text-sm font-medium text-slate-700"
                        >
                            Fee recipient
                        </label>
                        <input
                            id="provision-fee"
                            v-model="feeRecipient"
                            type="text"
                            placeholder="0x… (laisser vide pour défaut domaine)"
                            class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                        />
                    </div>
                    <div>
                        <label
                            for="provision-graffiti"
                            class="block text-sm font-medium text-slate-700"
                        >
                            Graffiti
                        </label>
                        <input
                            id="provision-graffiti"
                            v-model="graffiti"
                            type="text"
                            class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                        />
                    </div>
                    <div class="grid grid-cols-2 gap-3">
                        <div>
                            <label
                                for="provision-min-bid"
                                class="block text-sm font-medium text-slate-700"
                            >
                                MEV min bid
                            </label>
                            <input
                                id="provision-min-bid"
                                v-model="mevMinBid"
                                type="text"
                                placeholder="ex. 0.05"
                                class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                            />
                        </div>
                        <div>
                            <label
                                for="provision-build-factor"
                                class="block text-sm font-medium text-slate-700"
                            >
                                MEV build factor
                            </label>
                            <input
                                id="provision-build-factor"
                                v-model.number="mevBuildFactor"
                                type="number"
                                min="0"
                                class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                            />
                        </div>
                    </div>
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
