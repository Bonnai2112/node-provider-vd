<script setup lang="ts">
defineProps<{
    text: string;
}>();
</script>

<template>
    <span class="group/tooltip relative inline-flex shrink-0">
        <slot />
        <span
            role="tooltip"
            class="pointer-events-none absolute bottom-full left-1/2 z-10 mb-2 -translate-x-1/2 whitespace-nowrap rounded-md bg-slate-900 px-2 py-1 text-xs font-medium text-white opacity-0 shadow-sm transition-opacity duration-150 group-hover/tooltip:opacity-100 group-focus-within/tooltip:opacity-100"
        >
            {{ text }}
        </span>
    </span>
</template>
