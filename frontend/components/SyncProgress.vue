<script setup lang="ts">
import type { ClSync, ElSync } from '~/types/node';

type Sync = ElSync | ClSync;

const props = defineProps<{
    label: string;
    sync: Sync | null;
}>();

function isElSync(sync: Sync): sync is ElSync {
    return 'currentBlock' in sync;
}

function formatEta(iso: string | null): string | null {
    if (!iso) return null;
    const target = new Date(iso).getTime();
    const now = Date.now();
    const seconds = Math.max(0, Math.round((target - now) / 1000));
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return `${minutes}min`;
    const hours = Math.floor(minutes / 60);
    const remaining = minutes % 60;
    return `${hours}h${remaining.toString().padStart(2, '0')}`;
}

const eta = computed(() => formatEta(props.sync?.etaCompleteAt ?? null));

const kindClass: Record<string, string> = {
    SYNCED: 'bg-emerald-100 text-emerald-800 ring-emerald-200',
    SYNCING: 'bg-sky-100 text-sky-800 ring-sky-200',
    NOT_SYNCING: 'bg-zinc-100 text-zinc-700 ring-zinc-200',
};
</script>

<template>
    <div class="rounded-lg border border-slate-200 p-3">
        <div class="flex items-center justify-between">
            <span class="text-xs font-medium uppercase text-slate-500">
                {{ label }}
            </span>
            <span
                v-if="sync"
                :class="kindClass[sync.kind] ?? 'bg-slate-100 text-slate-700 ring-slate-200'"
                class="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ring-1 ring-inset"
            >
                {{ sync.kind }}
            </span>
            <span v-else class="text-xs text-slate-400">N/A</span>
        </div>

        <template v-if="sync && sync.kind === 'SYNCING'">
            <div class="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100">
                <div
                    class="h-full bg-sky-500 transition-all"
                    :style="{ width: `${Math.min(100, sync.percentage ?? 0)}%` }"
                />
            </div>
            <div class="mt-2 flex justify-between text-xs text-slate-600">
                <span>
                    <template v-if="isElSync(sync)">
                        block {{ sync.currentBlock }} / {{ sync.highestBlock }}
                    </template>
                    <template v-else>
                        slot {{ sync.headSlot }}, distance {{ sync.syncDistance }}
                    </template>
                </span>
                <span>
                    {{
                        sync.percentage !== null
                            ? `${sync.percentage.toFixed(1)}%`
                            : ''
                    }}
                </span>
            </div>
            <div class="mt-1 flex justify-between text-xs text-slate-500">
                <span>
                    <template v-if="isElSync(sync) && sync.blocksPerSecond !== null">
                        {{ sync.blocksPerSecond.toFixed(1) }} blocks/s
                    </template>
                    <template v-else-if="!isElSync(sync) && sync.slotsPerSecond !== null">
                        {{ sync.slotsPerSecond.toFixed(2) }} slots/s
                    </template>
                </span>
                <span v-if="eta">ETA {{ eta }}</span>
            </div>
        </template>

        <p v-else-if="sync && sync.kind === 'SYNCED'" class="mt-2 text-xs text-slate-600">
            À jour.
        </p>
        <p v-else-if="sync" class="mt-2 text-xs text-slate-500">
            Pas en synchronisation.
        </p>
    </div>
</template>
