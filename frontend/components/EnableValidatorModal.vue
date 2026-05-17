<script setup lang="ts">
const props = defineProps<{
    open: boolean;
    submitting?: boolean;
    error?: string | null;
    initialFeeRecipient?: string | null;
    initialGraffiti?: string | null;
}>();

const emit = defineEmits<{
    submit: [payload: { feeRecipient: string; graffiti: string | null }];
    close: [];
}>();

const feeRecipient = ref('');
const graffiti = ref('');

watch(
    () => props.open,
    (isOpen) => {
        if (isOpen) {
            feeRecipient.value =
                props.initialFeeRecipient && props.initialFeeRecipient !== ''
                    ? props.initialFeeRecipient
                    : '';
            graffiti.value = props.initialGraffiti ?? '';
        }
    },
);

function onSubmit() {
    const trimmedGraffiti = graffiti.value.trim();
    emit('submit', {
        feeRecipient: feeRecipient.value.trim(),
        graffiti: trimmedGraffiti === '' ? null : trimmedGraffiti,
    });
}
</script>

<template>
    <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="enable-validator-title"
        @click.self="emit('close')"
    >
        <div
            class="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-lg ring-1 ring-slate-200"
        >
            <h2
                id="enable-validator-title"
                class="text-lg font-semibold text-slate-900"
            >
                Activer le validateur
            </h2>
            <p class="mt-1 text-sm text-slate-500">
                Le client validateur sera lancé à côté du beacon. Le nœud reste
                Ready pendant la reconfiguration.
            </p>

            <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
                <div>
                    <label
                        for="enable-validator-fee"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Fee recipient
                    </label>
                    <input
                        id="enable-validator-fee"
                        v-model="feeRecipient"
                        type="text"
                        placeholder="0x…"
                        required
                        pattern="^0x[a-fA-F0-9]{40}$"
                        class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                    />
                    <p class="mt-1 text-xs text-slate-500">
                        Adresse Ethereum qui recevra les MEV / priority fees.
                    </p>
                </div>

                <div>
                    <label
                        for="enable-validator-graffiti"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Graffiti (optionnel)
                    </label>
                    <input
                        id="enable-validator-graffiti"
                        v-model="graffiti"
                        type="text"
                        maxlength="32"
                        class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                    />
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
                        class="rounded-md bg-violet-600 px-3 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:bg-slate-400"
                        :disabled="submitting"
                    >
                        {{ submitting ? 'Activation…' : 'Activer' }}
                    </button>
                </div>
            </form>
        </div>
    </div>
</template>
