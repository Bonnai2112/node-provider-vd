<script setup lang="ts">
import type { NodeStatusKind } from '~/types/node';

const props = defineProps<{ status: NodeStatusKind }>();

const palette: Record<NodeStatusKind, string> = {
    REQUESTED: 'bg-slate-100 text-slate-700 ring-slate-200',
    PROVISIONING: 'bg-amber-100 text-amber-800 ring-amber-200',
    SYNCING: 'bg-sky-100 text-sky-800 ring-sky-200',
    READY: 'bg-emerald-100 text-emerald-800 ring-emerald-200',
    DEGRADED: 'bg-orange-100 text-orange-800 ring-orange-200',
    TERMINATING: 'bg-zinc-100 text-zinc-700 ring-zinc-200',
    TERMINATED: 'bg-zinc-200 text-zinc-700 ring-zinc-300',
    FAILED: 'bg-rose-100 text-rose-800 ring-rose-200',
};

const classes = computed(() => palette[props.status]);
</script>

<template>
    <span
        :class="classes"
        class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset"
        :data-testid="`status-badge-${status}`"
    >
        {{ status }}
    </span>
</template>
