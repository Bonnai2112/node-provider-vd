<script setup lang="ts">
const props = defineProps<{
    open: boolean;
    pubkey: string | null;
    submitting?: boolean;
    error?: string | null;
}>();

const emit = defineEmits<{
    submit: [payload: { amountEth: number; keystorePassword: string }];
    close: [];
}>();

const amountEth = ref<number>(32);
const keystorePassword = ref('');

watch(
    () => props.open,
    (isOpen) => {
        if (isOpen) {
            amountEth.value = 32;
            keystorePassword.value = '';
        }
    },
);

const pubkeyShort = computed(() => {
    if (!props.pubkey) return '';
    const s = props.pubkey.startsWith('0x')
        ? props.pubkey.slice(2)
        : props.pubkey;
    return '0x' + s.slice(0, 8) + '…' + s.slice(-6);
});

function onSubmit() {
    if (amountEth.value < 1 || !keystorePassword.value) return;
    emit('submit', {
        amountEth: amountEth.value,
        keystorePassword: keystorePassword.value,
    });
}
</script>

<template>
    <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="topup-title"
        @click.self="emit('close')"
    >
        <div
            class="w-full max-w-md rounded-xl bg-white p-6 shadow-lg ring-1 ring-slate-200"
        >
            <h2 id="topup-title" class="text-lg font-semibold text-slate-900">
                Top-up d'un validateur actif
            </h2>
            <p class="mt-1 text-sm text-slate-500">
                Génère un <code>deposit_data.json</code> signé pour augmenter le
                stake d'un validateur déjà actif (credentials 0x02 / Pectra). Tu
                téléchargeras le fichier puis soumettras la transaction au
                contrat de dépôt avec ton wallet.
            </p>
            <p
                v-if="pubkey"
                class="mt-2 font-mono text-xs text-slate-600"
                :title="pubkey ?? ''"
            >
                {{ pubkeyShort }}
            </p>

            <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
                <div>
                    <label
                        for="topup-amount"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Montant (ETH)
                    </label>
                    <input
                        id="topup-amount"
                        v-model.number="amountEth"
                        type="number"
                        min="1"
                        step="0.000000001"
                        required
                        data-testid="topup-amount-input"
                        class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                    />
                    <p class="mt-1 text-xs text-slate-500">
                        Minimum 1 ETH. Précision max : 1 gwei (1e-9 ETH).
                    </p>
                </div>
                <div>
                    <label
                        for="topup-password"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Mot de passe du keystore
                    </label>
                    <input
                        id="topup-password"
                        v-model="keystorePassword"
                        type="password"
                        required
                        data-testid="topup-password-input"
                        class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                    />
                    <p class="mt-1 text-xs text-slate-500">
                        Celui retourné à la génération initiale (ou fourni à
                        l'import).
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
                        data-testid="topup-submit-btn"
                        :disabled="
                            submitting ||
                            amountEth < 1 ||
                            keystorePassword.length === 0
                        "
                    >
                        {{ submitting ? 'Génération…' : 'Générer le top-up' }}
                    </button>
                </div>
            </form>
        </div>
    </div>
</template>
