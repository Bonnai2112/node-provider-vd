<script setup lang="ts">
useHead({
    title: 'Node Provider',
});
</script>

<template>
    <div class="min-h-screen bg-slate-50 text-slate-900">
        <AppNav />
        <main class="mx-auto max-w-5xl px-6 py-10">
            <NuxtPage />
        </main>
    </div>
</template>
