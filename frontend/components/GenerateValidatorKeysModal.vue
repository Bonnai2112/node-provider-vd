<script setup lang="ts">
import type { GenerateValidatorKeysResponse } from '~/types/node';

const props = defineProps<{
    open: boolean;
    submitting?: boolean;
    error?: string | null;
    result?: GenerateValidatorKeysResponse | null;
}>();

const emit = defineEmits<{
    submit: [payload: { count: number; withdrawalAddress: string }];
    close: [];
}>();

const count = ref(1);
const withdrawalAddress = ref('');
const ack = ref(false);
const showSecrets = ref(false);

watch(
    () => props.open,
    (isOpen) => {
        if (isOpen) {
            count.value = 1;
            withdrawalAddress.value = '';
            ack.value = false;
            showSecrets.value = false;
        }
    },
);

function onSubmit() {
    emit('submit', {
        count: count.value,
        withdrawalAddress: withdrawalAddress.value.trim(),
    });
}

async function copy(text: string) {
    try {
        await navigator.clipboard.writeText(text);
    } catch {
        // l'utilisateur peut copier manuellement, pas grave
    }
}

const canClose = computed(() => !props.result || ack.value);

function tryClose() {
    if (canClose.value) emit('close');
}
</script>

<template>
    <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="genkeys-title"
        @click.self="tryClose"
    >
        <div
            class="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-lg ring-1 ring-slate-200"
        >
            <h2 id="genkeys-title" class="text-lg font-semibold text-slate-900">
                Générer des clés validator
            </h2>

            <template v-if="!result">
                <p class="mt-1 text-sm text-slate-500">
                    deposit-cli génère des clés à partir d'un nouveau mnemonic.
                    Le mnemonic et le mot de passe ne seront affichés qu'une seule
                    fois.
                </p>

                <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
                    <div>
                        <label
                            for="genkeys-count"
                            class="block text-sm font-medium text-slate-700"
                        >
                            Nombre de clés
                        </label>
                        <input
                            id="genkeys-count"
                            v-model.number="count"
                            type="number"
                            min="1"
                            max="100"
                            required
                            class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                        />
                    </div>
                    <div>
                        <label
                            for="genkeys-withdrawal"
                            class="block text-sm font-medium text-slate-700"
                        >
                            Withdrawal address
                        </label>
                        <input
                            id="genkeys-withdrawal"
                            v-model="withdrawalAddress"
                            type="text"
                            placeholder="0x…"
                            required
                            pattern="^0x[a-fA-F0-9]{40}$"
                            class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
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
                            class="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
                            :disabled="submitting"
                        >
                            {{ submitting ? 'Génération…' : 'Générer' }}
                        </button>
                    </div>
                </form>
            </template>

            <template v-else>
                <div class="mt-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800 ring-1 ring-amber-200">
                    ⚠️ Le mnemonic et le mot de passe ne seront PAS affichés à
                    nouveau. Sauvegarde-les hors-ligne avant de fermer.
                </div>

                <div class="mt-4 space-y-3">
                    <div>
                        <div class="flex items-center justify-between">
                            <span class="text-xs font-medium uppercase text-slate-500">
                                Mnemonic
                            </span>
                            <div class="flex items-center gap-2">
                                <button
                                    type="button"
                                    class="text-xs font-medium text-slate-600 hover:text-slate-900"
                                    @click="showSecrets = !showSecrets"
                                >
                                    {{ showSecrets ? 'Masquer' : 'Révéler' }}
                                </button>
                                <button
                                    type="button"
                                    class="text-xs font-medium text-slate-600 hover:text-slate-900"
                                    @click="copy(result.mnemonic)"
                                >
                                    Copier
                                </button>
                            </div>
                        </div>
                        <pre
                            class="mt-1 whitespace-pre-wrap break-words rounded-md bg-slate-900 px-3 py-2 font-mono text-xs text-slate-100"
                            :class="{ 'blur-sm select-none': !showSecrets }"
                        >{{ result.mnemonic }}</pre>
                    </div>

                    <div>
                        <div class="flex items-center justify-between">
                            <span class="text-xs font-medium uppercase text-slate-500">
                                Password keystore
                            </span>
                            <button
                                type="button"
                                class="text-xs font-medium text-slate-600 hover:text-slate-900"
                                @click="copy(result.password)"
                            >
                                Copier
                            </button>
                        </div>
                        <pre
                            class="mt-1 break-all rounded-md bg-slate-900 px-3 py-2 font-mono text-xs text-slate-100"
                            :class="{ 'blur-sm select-none': !showSecrets }"
                        >{{ result.password }}</pre>
                    </div>

                    <div>
                        <span class="text-xs font-medium uppercase text-slate-500">
                            Pubkeys générées ({{ result.keys.length }})
                        </span>
                        <ul class="mt-1 max-h-32 overflow-y-auto rounded-md border border-slate-200 p-2 text-xs">
                            <li
                                v-for="k in result.keys"
                                :key="k.id"
                                class="break-all font-mono text-slate-800"
                            >
                                {{ k.pubkey }}
                            </li>
                        </ul>
                    </div>
                </div>

                <label class="mt-4 flex items-center gap-2 text-sm text-slate-700">
                    <input
                        v-model="ack"
                        type="checkbox"
                        class="h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-500"
                    />
                    J'ai sauvegardé le mnemonic et le mot de passe.
                </label>

                <div class="mt-4 flex justify-end">
                    <button
                        type="button"
                        class="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
                        :disabled="!ack"
                        @click="emit('close')"
                    >
                        Fermer
                    </button>
                </div>
            </template>
        </div>
    </div>
</template>
