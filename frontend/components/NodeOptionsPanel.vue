<script setup lang="ts">
import type { NodeOptions } from '~/types/node';

defineProps<{ options: NodeOptions }>();
</script>

<template>
    <div class="rounded-lg border border-slate-200 p-4">
        <h3 class="text-sm font-semibold text-slate-900">Options</h3>
        <dl class="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
                <dt class="text-xs font-medium uppercase text-slate-500">
                    Validator
                </dt>
                <dd class="mt-0.5 text-sm text-slate-800">
                    {{ options.validator ? 'Activé' : 'Désactivé' }}
                </dd>
            </div>
            <div>
                <dt class="text-xs font-medium uppercase text-slate-500">
                    MEV-Boost
                </dt>
                <dd class="mt-0.5 text-sm text-slate-800">
                    {{ options.mevBoost ? 'Activé' : 'Désactivé' }}
                </dd>
            </div>
            <div class="sm:col-span-2">
                <dt class="text-xs font-medium uppercase text-slate-500">
                    Fee recipient
                </dt>
                <dd class="mt-0.5 break-all font-mono text-sm text-slate-800">
                    {{ options.feeRecipient || '—' }}
                </dd>
            </div>
            <div v-if="options.graffiti">
                <dt class="text-xs font-medium uppercase text-slate-500">
                    Graffiti
                </dt>
                <dd class="mt-0.5 text-sm text-slate-800">
                    {{ options.graffiti }}
                </dd>
            </div>
            <div v-if="options.mevMinBid">
                <dt class="text-xs font-medium uppercase text-slate-500">
                    MEV min bid
                </dt>
                <dd class="mt-0.5 font-mono text-sm text-slate-800">
                    {{ options.mevMinBid }}
                </dd>
            </div>
            <div v-if="options.mevBuildFactor !== null">
                <dt class="text-xs font-medium uppercase text-slate-500">
                    MEV build factor
                </dt>
                <dd class="mt-0.5 font-mono text-sm text-slate-800">
                    {{ options.mevBuildFactor }}
                </dd>
            </div>
        </dl>
    </div>
</template>
