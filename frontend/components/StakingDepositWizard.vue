<script setup lang="ts">
import type { Network } from '~/types/node';

const props = defineProps<{
    nodeId: string;
    network: Network;
    keysCount: number;
}>();

const launchpadUrl = computed<string>(() =>
    props.network === 'HOODI'
        ? 'https://hoodi.launchpad.ethereum.org/'
        : 'https://sepolia.launchpad.ethereum.org/',
);

const totalEth = computed<number>(() => props.keysCount * 32);
</script>

<template>
    <section class="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
        <h2 class="text-lg font-semibold text-slate-900">Dépôt staking</h2>
        <p class="mt-1 text-sm text-slate-600">
            Dépose les fichiers téléchargés dans la section ci-dessus sur le
            launchpad officiel et signe les transactions avec ton propre
            wallet — la clé privée du déposant ne quitte jamais ton
            navigateur.
        </p>

        <div
            v-if="keysCount === 0"
            class="mt-4 rounded-md border border-dashed border-slate-200 p-4 text-center text-sm text-slate-500"
        >
            Génère ou importe d'abord des clés validator.
        </div>

        <template v-else>
            <dl
                class="mt-4 grid grid-cols-1 gap-4 rounded-md bg-slate-50 p-4 sm:grid-cols-3"
            >
                <div>
                    <dt class="text-xs font-medium uppercase text-slate-500">
                        Réseau
                    </dt>
                    <dd class="mt-0.5 text-sm font-semibold text-slate-800">
                        {{ network }}
                    </dd>
                </div>
                <div>
                    <dt class="text-xs font-medium uppercase text-slate-500">
                        Validators
                    </dt>
                    <dd class="mt-0.5 text-sm font-semibold text-slate-800">
                        {{ keysCount }}
                    </dd>
                </div>
                <div>
                    <dt class="text-xs font-medium uppercase text-slate-500">
                        Total à déposer
                    </dt>
                    <dd class="mt-0.5 text-sm font-semibold text-slate-800">
                        {{ totalEth }} ETH
                        <span class="text-xs font-normal text-slate-500">
                            ({{ keysCount }} × 32)
                        </span>
                    </dd>
                </div>
            </dl>

            <div class="mt-6">
                <a
                    :href="launchpadUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
                    data-testid="launchpad-link"
                >
                    Ouvrir le launchpad ↗
                </a>
            </div>
        </template>
    </section>
</template>
