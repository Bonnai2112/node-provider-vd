<script setup lang="ts">
const props = defineProps<{
    open: boolean;
    submitting?: boolean;
    error?: string | null;
    initialMevMinBid?: string | null;
    initialMevBuildFactor?: number | null;
}>();

const emit = defineEmits<{
    submit: [payload: { mevMinBid: string; mevBuildFactor: number }];
    close: [];
}>();

const mevMinBid = ref('0.05');
const mevBuildFactor = ref(90);

watch(
    () => props.open,
    (isOpen) => {
        if (isOpen) {
            mevMinBid.value =
                props.initialMevMinBid && props.initialMevMinBid !== ''
                    ? props.initialMevMinBid
                    : '0.05';
            mevBuildFactor.value = props.initialMevBuildFactor ?? 90;
        }
    },
);

function onSubmit() {
    emit('submit', {
        mevMinBid: mevMinBid.value.trim(),
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
        aria-labelledby="enable-mev-title"
        @click.self="emit('close')"
    >
        <div
            class="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-lg ring-1 ring-slate-200"
        >
            <h2
                id="enable-mev-title"
                class="text-lg font-semibold text-slate-900"
            >
                Activer MEV-Boost
            </h2>
            <p class="mt-1 text-sm text-slate-500">
                Le service mev-boost interrogera les relays configurés par
                eth-docker.
            </p>

            <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
                <div>
                    <label
                        for="enable-mev-min-bid"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Min bid (ETH)
                    </label>
                    <input
                        id="enable-mev-min-bid"
                        v-model="mevMinBid"
                        type="text"
                        required
                        pattern="^\d+(\.\d+)?$"
                        class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                    />
                    <p class="mt-1 text-xs text-slate-500">
                        En-dessous de ce montant, mev-boost ignore le bloc relay
                        et le validateur construit lui-même.
                    </p>
                </div>

                <div>
                    <label
                        for="enable-mev-build-factor"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Build factor
                    </label>
                    <input
                        id="enable-mev-build-factor"
                        v-model.number="mevBuildFactor"
                        type="number"
                        min="1"
                        max="100"
                        required
                        class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                    />
                    <p class="mt-1 text-xs text-slate-500">
                        Pourcentage [1, 100]. Pondère la valeur relay vs.
                        construction locale.
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
                        class="rounded-md bg-fuchsia-600 px-3 py-2 text-sm font-medium text-white hover:bg-fuchsia-700 disabled:cursor-not-allowed disabled:bg-slate-400"
                        :disabled="submitting"
                    >
                        {{ submitting ? 'Activation…' : 'Activer' }}
                    </button>
                </div>
            </form>
        </div>
    </div>
</template>
