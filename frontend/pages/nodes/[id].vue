<script setup lang="ts">
import { useNodesStore } from '~/stores/nodes';
import { TERMINAL_STATUSES } from '~/types/node';

const route = useRoute();
const router = useRouter();
const id = computed(() => String(route.params.id));

const api = useNodesApi();
const store = useNodesStore();

const node = computed(() => store.get(id.value));

await useAsyncData(() => `node-${id.value}`, () => store.fetchOne(api, id.value), {
    watch: [id],
});

const POLL_INTERVAL_MS = 5_000;
let pollHandle: ReturnType<typeof setInterval> | null = null;

function shouldPoll(): boolean {
    const current = node.value;
    if (!current) return true;
    return !TERMINAL_STATUSES.has(current.status);
}

function startPolling() {
    if (pollHandle !== null) return;
    pollHandle = setInterval(async () => {
        if (!shouldPoll()) {
            stopPolling();
            return;
        }
        try {
            await store.fetchOne(api, id.value);
        } catch {
            // erreur exposée par le store
        }
    }, POLL_INTERVAL_MS);
}

function stopPolling() {
    if (pollHandle !== null) {
        clearInterval(pollHandle);
        pollHandle = null;
    }
}

onMounted(() => {
    if (shouldPoll()) startPolling();
});

watch(node, (current) => {
    if (current && !TERMINAL_STATUSES.has(current.status)) {
        startPolling();
    } else {
        stopPolling();
    }
});

onBeforeUnmount(stopPolling);

const terminating = ref(false);
const terminateError = ref<string | null>(null);

async function onTerminate() {
    if (!node.value) return;
    if (!confirm('Terminer ce nœud ? Cette action est irréversible.'))
        return;
    terminating.value = true;
    terminateError.value = null;
    try {
        await store.terminate(api, id.value);
        await store.fetchOne(api, id.value);
        startPolling();
    } catch (e) {
        terminateError.value = e instanceof Error ? e.message : 'Erreur';
    } finally {
        terminating.value = false;
    }
}

const canTerminate = computed(() => {
    const current = node.value;
    if (!current) return false;
    return current.status !== 'TERMINATED' && current.status !== 'TERMINATING';
});

function formatRelative(iso: string | null): string {
    if (!iso) return '—';
    const seconds = Math.max(
        0,
        Math.round((Date.now() - new Date(iso).getTime()) / 1000),
    );
    if (seconds < 60) return `il y a ${seconds}s`;
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return `il y a ${minutes}min`;
    const hours = Math.floor(minutes / 60);
    return `il y a ${hours}h`;
}
</script>

<template>
    <section class="space-y-6">
        <div class="flex items-center gap-3">
            <button
                type="button"
                class="text-sm text-slate-600 hover:text-slate-900"
                @click="router.push('/nodes')"
            >
                ← Retour
            </button>
        </div>

        <div
            v-if="store.detailError && !node"
            class="rounded-md bg-rose-50 px-4 py-3 text-sm text-rose-700 ring-1 ring-rose-200"
            role="alert"
        >
            Erreur : {{ store.detailError }}
        </div>

        <template v-else-if="node">
            <article
                class="space-y-6 rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200"
            >
                <header class="flex items-start justify-between gap-4">
                    <div>
                        <h1 class="text-xl font-semibold tracking-tight">
                            Nœud
                            <span class="font-mono text-base text-slate-700">
                                {{ node.id }}
                            </span>
                        </h1>
                        <p class="mt-1 text-sm text-slate-600">
                            {{ node.network }} · {{ node.executionLayer }}+{{
                                node.consensusLayer
                            }}
                        </p>
                    </div>
                    <div class="flex flex-col items-end gap-1">
                        <NodeStatusBadge :status="node.status" />
                        <span
                            v-if="node.options.validator"
                            class="inline-flex items-center rounded-full bg-violet-100 px-2.5 py-0.5 text-xs font-medium text-violet-800 ring-1 ring-inset ring-violet-200"
                        >
                            Validator
                        </span>
                        <span
                            v-if="node.options.mevBoost"
                            class="inline-flex items-center rounded-full bg-fuchsia-100 px-2.5 py-0.5 text-xs font-medium text-fuchsia-800 ring-1 ring-inset ring-fuchsia-200"
                        >
                            MEV-Boost
                        </span>
                    </div>
                </header>

                <dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div>
                        <dt class="text-xs font-medium uppercase text-slate-500">
                            Owner
                        </dt>
                        <dd class="mt-1 font-mono text-sm text-slate-800">
                            {{ node.ownerId }}
                        </dd>
                    </div>
                    <div>
                        <dt class="text-xs font-medium uppercase text-slate-500">
                            Endpoint JSON-RPC
                        </dt>
                        <dd
                            class="mt-1 break-all font-mono text-sm text-slate-800"
                            data-testid="rpc-endpoint"
                        >
                            <template v-if="node.endpoint">
                                {{ node.endpoint }}
                            </template>
                            <span v-else class="text-slate-400">
                                Indisponible tant que le nœud n'est pas Ready.
                            </span>
                        </dd>
                    </div>
                    <div>
                        <dt class="text-xs font-medium uppercase text-slate-500">
                            Peers
                        </dt>
                        <dd class="mt-1 font-mono text-sm text-slate-800">
                            {{ node.peers ?? '—' }}
                        </dd>
                    </div>
                    <div>
                        <dt class="text-xs font-medium uppercase text-slate-500">
                            Dernière observation
                        </dt>
                        <dd class="mt-1 text-sm text-slate-800">
                            {{ formatRelative(node.lastObservedAt) }}
                        </dd>
                    </div>
                    <div v-if="node.reason" class="sm:col-span-2">
                        <dt class="text-xs font-medium uppercase text-slate-500">
                            Détail
                        </dt>
                        <dd class="mt-1 text-sm text-slate-800">
                            {{ node.reason }}
                        </dd>
                    </div>
                </dl>

                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <SyncProgress label="Execution layer" :sync="node.elSync" />
                    <SyncProgress label="Consensus layer" :sync="node.clSync" />
                </div>

                <NodeOptionsPanel :options="node.options" />

                <div class="flex items-center gap-3 border-t border-slate-200 pt-4">
                    <button
                        type="button"
                        class="rounded-md bg-rose-600 px-3 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:bg-slate-400"
                        :disabled="!canTerminate || terminating"
                        data-testid="terminate-btn"
                        @click="onTerminate"
                    >
                        {{ terminating ? 'En cours…' : 'Terminer' }}
                    </button>
                    <p
                        v-if="terminateError"
                        class="text-sm text-rose-700"
                        role="alert"
                    >
                        {{ terminateError }}
                    </p>
                </div>
            </article>

            <ValidatorKeysSection
                :node-id="node.id"
                :network="node.network"
                :enabled="node.options.validator"
            />

            <StakingDepositWizard
                v-if="node.options.validator"
                :node-id="node.id"
                :network="node.network"
                :keys-count="store.keysFor(node.id).length"
            />
        </template>

        <div v-else class="text-sm text-slate-500">Chargement…</div>
    </section>
</template>
