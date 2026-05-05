<script setup lang="ts">
const props = defineProps<{
    open: boolean;
    submitting?: boolean;
    error?: string | null;
}>();

const emit = defineEmits<{
    submit: [payload: { files: File[]; password: string }];
    close: [];
}>();

const files = ref<File[]>([]);
const password = ref('');

watch(
    () => props.open,
    (isOpen) => {
        if (isOpen) {
            files.value = [];
            password.value = '';
        }
    },
);

function onFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    files.value = input.files ? Array.from(input.files) : [];
}

function onSubmit() {
    if (files.value.length === 0) return;
    emit('submit', { files: files.value, password: password.value });
}
</script>

<template>
    <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="importkeys-title"
        @click.self="emit('close')"
    >
        <div
            class="w-full max-w-md rounded-xl bg-white p-6 shadow-lg ring-1 ring-slate-200"
        >
            <h2 id="importkeys-title" class="text-lg font-semibold text-slate-900">
                Importer des clés validator
            </h2>
            <p class="mt-1 text-sm text-slate-500">
                Upload des keystores JSON (EIP-2335) et le mot de passe associé.
            </p>

            <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
                <div>
                    <label
                        for="import-files"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Keystores JSON
                    </label>
                    <input
                        id="import-files"
                        type="file"
                        accept="application/json,.json"
                        multiple
                        required
                        class="mt-1 block w-full text-sm text-slate-700 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm file:font-medium file:text-slate-700 hover:file:bg-slate-200"
                        @change="onFileChange"
                    />
                    <p v-if="files.length > 0" class="mt-1 text-xs text-slate-500">
                        {{ files.length }} fichier(s) sélectionné(s).
                    </p>
                </div>
                <div>
                    <label
                        for="import-password"
                        class="block text-sm font-medium text-slate-700"
                    >
                        Mot de passe
                    </label>
                    <input
                        id="import-password"
                        v-model="password"
                        type="password"
                        required
                        class="mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
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
                        :disabled="submitting || files.length === 0"
                    >
                        {{ submitting ? 'Import…' : 'Importer' }}
                    </button>
                </div>
            </form>
        </div>
    </div>
</template>
