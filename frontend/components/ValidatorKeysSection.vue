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
</script>

<template>
    <section class="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
        <div class="flex items-center justify-between">
            <div>
                <h2 class="text-lg font-semibold text-slate-900">
                    Clés validator
                </h2>
                <p class="mt-1 text-sm text-slate-600">
                    <template v-if="enabled">
                        Génère ou importe les clés à signer par le validator
                        client.
                    </template>
                    <template v-else>
                        Activer l'option « validator » sur le nœud pour utiliser
                        ces clés.
                    </template>
                </p>
            </div>
            <div v-if="enabled" class="flex gap-2">
                <button
                    type="button"
                    class="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                    data-testid="keys-import-btn"
                    @click="openImport"
                >
                    Importer
                </button>
                <button
                    type="button"
                    class="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
                    data-testid="keys-generate-btn"
                    @click="openGenerate"
                >
                    Générer
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
                class="flex items-center justify-between gap-4 px-3 py-2"
            >
                <span class="break-all font-mono text-xs text-slate-800">
                    {{ k.pubkey }}
                </span>
                <span class="shrink-0 text-xs text-slate-500">
                    {{ new Date(k.importedAt).toLocaleString() }}
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
