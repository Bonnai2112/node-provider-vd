<script setup lang="ts">
import { useNodesStore } from '~/stores/nodes';
import type { GenerateValidatorKeysResponse, ValidatorKey } from '~/types/node';

const props = defineProps<{
    nodeId: string;
    enabled: boolean;
}>();

const api = useNodesApi();
const store = useNodesStore();

const keys = computed<ValidatorKey[]>(() => store.keysFor(props.nodeId));

const generateOpen = ref(false);
const generateSubmitting = ref(false);
const generateError = ref<string | null>(null);
const generateResult = ref<GenerateValidatorKeysResponse | null>(null);

const importOpen = ref(false);
const importSubmitting = ref(false);
const importError = ref<string | null>(null);

const rowError = ref<string | null>(null);
const copiedPubkey = ref<string | null>(null);
const downloadingKeystore = ref<string | null>(null);
const downloadingDeposit = ref<string | null>(null);

async function refreshKeys() {
    try {
        await store.fetchValidatorKeys(api, props.nodeId);
    } catch {
        // erreur exposée par le store
    }
}

onMounted(() => {
    if (props.enabled) refreshKeys();
});

watch(
    () => props.enabled,
    (isEnabled) => {
        if (isEnabled && keys.value.length === 0) refreshKeys();
    },
);

function openGenerate() {
    generateError.value = null;
    generateResult.value = null;
    generateOpen.value = true;
}

function closeGenerate() {
    generateOpen.value = false;
    generateResult.value = null;
}

async function onGenerateSubmit(payload: { count: number; withdrawalAddress: string }) {
    generateSubmitting.value = true;
    generateError.value = null;
    try {
        const result = await api.generateValidatorKeys(props.nodeId, payload);
        generateResult.value = result;
        store.appendValidatorKeys(props.nodeId, result.keys);
    } catch (e) {
        generateError.value = e instanceof Error ? e.message : 'Erreur';
    } finally {
        generateSubmitting.value = false;
    }
}

function openImport() {
    importError.value = null;
    importOpen.value = true;
}

async function onImportSubmit(payload: { files: File[]; password: string }) {
    importSubmitting.value = true;
    importError.value = null;
    try {
        const imported = await api.importValidatorKeys(
            props.nodeId,
            payload.files,
            payload.password,
        );
        store.appendValidatorKeys(props.nodeId, imported);
        importOpen.value = false;
    } catch (e) {
        importError.value = e instanceof Error ? e.message : 'Erreur';
    } finally {
        importSubmitting.value = false;
    }
}

function pubkeyShort(pubkey: string): string {
    const s = pubkey.startsWith('0x') ? pubkey.slice(2) : pubkey;
    return s.slice(0, 12);
}

function triggerBrowserDownload(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
}

async function copyPubkey(pubkey: string) {
    rowError.value = null;
    try {
        await navigator.clipboard.writeText(pubkey);
        copiedPubkey.value = pubkey;
        setTimeout(() => {
            if (copiedPubkey.value === pubkey) copiedPubkey.value = null;
        }, 1500);
    } catch (e) {
        rowError.value = e instanceof Error ? e.message : 'Copie impossible';
    }
}

async function downloadKeystoreFor(pubkey: string) {
    rowError.value = null;
    downloadingKeystore.value = pubkey;
    try {
        const blob = await api.downloadKeystoreFor(props.nodeId, pubkey);
        triggerBrowserDownload(blob, `keystore-${pubkeyShort(pubkey)}.json`);
    } catch (e) {
        rowError.value = e instanceof Error ? e.message : 'Erreur';
    } finally {
        downloadingKeystore.value = null;
    }
}

async function downloadDepositDataFor(pubkey: string) {
    rowError.value = null;
    downloadingDeposit.value = pubkey;
    try {
        const blob = await api.downloadDepositDataFor(props.nodeId, pubkey);
        triggerBrowserDownload(blob, `deposit_data-${pubkeyShort(pubkey)}.json`);
    } catch (e) {
        rowError.value = e instanceof Error ? e.message : 'Erreur';
    } finally {
        downloadingDeposit.value = null;
    }
}
</script>

<template>
    <section class="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
                <h2 class="text-lg font-semibold text-slate-900">
                    Clés validator
                </h2>
                <p class="mt-1 text-sm text-slate-600">
                    <template v-if="enabled">
                        Génère ou importe les clés. Pour chaque clé, tu peux
                        télécharger le keystore chiffré et son deposit_data.
                    </template>
                    <template v-else>
                        Activer l'option « validator » sur le nœud pour utiliser
                        ces clés.
                    </template>
                </p>
            </div>
            <div v-if="enabled" class="flex flex-nowrap items-center gap-2">
                <button
                    type="button"
                    class="inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                    title="Importer des keystores existants"
                    aria-label="Importer"
                    data-testid="keys-import-btn"
                    @click="openImport"
                >
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke-width="1.75"
                        stroke="currentColor"
                        class="h-4 w-4"
                        aria-hidden="true"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            d="M3 16.5v2.25A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75V16.5M16.5 7.5 12 3m0 0L7.5 7.5M12 3v13.5"
                        />
                    </svg>
                    <span>Importer</span>
                </button>
                <button
                    type="button"
                    class="inline-flex items-center gap-1.5 rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
                    title="Générer de nouvelles clés validator"
                    aria-label="Générer"
                    data-testid="keys-generate-btn"
                    @click="openGenerate"
                >
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke-width="1.75"
                        stroke="currentColor"
                        class="h-4 w-4"
                        aria-hidden="true"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            d="M9.813 15.904 9 18.75l-.813-2.846a4.5 4.5 0 0 0-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 0 0 3.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 0 0 3.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 0 0-3.09 3.09ZM18.259 8.715 18 9.75l-.259-1.035a3.375 3.375 0 0 0-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 0 0 2.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 0 0 2.456 2.456L21.75 6l-1.035.259a3.375 3.375 0 0 0-2.456 2.456Z"
                        />
                    </svg>
                    <span>Générer</span>
                </button>
            </div>
        </div>

        <div
            v-if="store.keysError"
            class="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-rose-200"
            role="alert"
        >
            {{ store.keysError }}
        </div>

        <div
            v-if="rowError"
            class="mt-4 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700 ring-1 ring-rose-200"
            role="alert"
        >
            {{ rowError }}
        </div>

        <div
            v-if="enabled && keys.length === 0 && !store.keysLoading"
            class="mt-4 rounded-md border border-dashed border-slate-200 p-4 text-center text-sm text-slate-500"
        >
            Aucune clé pour ce nœud.
        </div>

        <ul
            v-else-if="enabled && keys.length > 0"
            class="mt-4 divide-y divide-slate-200 rounded-md border border-slate-200"
        >
            <li
                v-for="k in keys"
                :key="k.id"
                class="flex items-center gap-3 px-3 py-2"
                data-testid="validator-key-row"
            >
                <span
                    class="flex-1 truncate font-mono text-xs text-slate-800"
                    :title="k.pubkey"
                >
                    {{ k.pubkey }}
                </span>
                <button
                    type="button"
                    class="shrink-0 rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                    :title="
                        copiedPubkey === k.pubkey
                            ? 'Copié !'
                            : 'Copier la pubkey'
                    "
                    :aria-label="
                        copiedPubkey === k.pubkey
                            ? 'Pubkey copiée'
                            : 'Copier la pubkey'
                    "
                    :data-testid="`copy-pubkey-btn-${k.id}`"
                    @click="copyPubkey(k.pubkey)"
                >
                    <svg
                        v-if="copiedPubkey === k.pubkey"
                        xmlns="http://www.w3.org/2000/svg"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke-width="2"
                        stroke="currentColor"
                        class="h-4 w-4 text-emerald-600"
                        aria-hidden="true"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            d="m4.5 12.75 6 6 9-13.5"
                        />
                    </svg>
                    <svg
                        v-else
                        xmlns="http://www.w3.org/2000/svg"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke-width="1.75"
                        stroke="currentColor"
                        class="h-4 w-4"
                        aria-hidden="true"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            d="M15.75 17.25v3.375c0 .621-.504 1.125-1.125 1.125h-9.75a1.125 1.125 0 0 1-1.125-1.125V7.875c0-.621.504-1.125 1.125-1.125H6.75a9.06 9.06 0 0 1 1.5.124m7.5 10.376h3.375c.621 0 1.125-.504 1.125-1.125V11.25c0-4.46-3.243-8.161-7.5-8.876a9.06 9.06 0 0 0-1.5-.124H9.375c-.621 0-1.125.504-1.125 1.125v3.5m7.5 10.375H9.375a1.125 1.125 0 0 1-1.125-1.125v-9.25m12 6.625v-1.875a3.375 3.375 0 0 0-3.375-3.375h-1.5a1.125 1.125 0 0 1-1.125-1.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H9.75"
                        />
                    </svg>
                </button>
                <button
                    type="button"
                    class="shrink-0 rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                    :disabled="downloadingDeposit === k.pubkey"
                    title="Télécharger deposit_data.json (cette clé)"
                    aria-label="Télécharger deposit_data pour cette clé"
                    :data-testid="`download-deposit-btn-${k.id}`"
                    @click="downloadDepositDataFor(k.pubkey)"
                >
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke-width="1.75"
                        stroke="currentColor"
                        class="h-4 w-4"
                        aria-hidden="true"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            d="M19.5 14.25v-2.625a3.375 3.375 0 0 0-3.375-3.375h-1.5A1.125 1.125 0 0 1 13.5 7.125v-1.5a3.375 3.375 0 0 0-3.375-3.375H8.25m2.25 12 3 3m0 0 3-3m-3 3v-6m-1.5-9H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 0 0-9-9Z"
                        />
                    </svg>
                </button>
                <button
                    type="button"
                    class="shrink-0 rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                    :disabled="downloadingKeystore === k.pubkey"
                    title="Télécharger le keystore chiffré (cette clé)"
                    aria-label="Télécharger le keystore pour cette clé"
                    :data-testid="`download-keystore-btn-${k.id}`"
                    @click="downloadKeystoreFor(k.pubkey)"
                >
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke-width="1.75"
                        stroke="currentColor"
                        class="h-4 w-4"
                        aria-hidden="true"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            d="M16.5 10.5V6.75a4.5 4.5 0 1 0-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 0 0 2.25-2.25v-6.75a2.25 2.25 0 0 0-2.25-2.25H6.75a2.25 2.25 0 0 0-2.25 2.25v6.75a2.25 2.25 0 0 0 2.25 2.25Z"
                        />
                    </svg>
                </button>
                <span
                    class="hidden shrink-0 text-xs text-slate-500 sm:inline"
                    :title="new Date(k.importedAt).toLocaleString()"
                >
                    {{ new Date(k.importedAt).toLocaleDateString() }}
                </span>
            </li>
        </ul>

        <GenerateValidatorKeysModal
            :open="generateOpen"
            :submitting="generateSubmitting"
            :error="generateError"
            :result="generateResult"
            @submit="onGenerateSubmit"
            @close="closeGenerate"
        />

        <ImportValidatorKeysModal
            :open="importOpen"
            :submitting="importSubmitting"
            :error="importError"
            @submit="onImportSubmit"
            @close="importOpen = false"
        />
    </section>
</template>
