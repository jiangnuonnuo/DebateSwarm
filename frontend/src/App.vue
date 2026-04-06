<script setup>
import { computed, onBeforeUnmount, watch } from 'vue';
import { RouterView, useRoute } from 'vue-router';
import Sidebar from './components/Sidebar.vue';
import Toast from './components/Toast.vue';
import CompanionPet from './components/CompanionPet.vue';
import { useSettingsStore } from './router/pinia';

const route = useRoute();
const hideSidebar = computed(() => route.meta?.hideSidebar);

const settingsStore = useSettingsStore();
const resolvedTheme = computed(() => (settingsStore.theme === 'dark' ? 'dark' : 'light'));
let switchRaf = 0;

watch(
    resolvedTheme,
    (theme) => {
        const root = document.documentElement;
        root.classList.add('theme-switching');
        root.setAttribute('data-theme', theme);
        root.style.colorScheme = theme;
        if (switchRaf) cancelAnimationFrame(switchRaf);
        switchRaf = requestAnimationFrame(() => {
            root.classList.remove('theme-switching');
            switchRaf = 0;
        });
    },
    { immediate: true }
);

onBeforeUnmount(() => {
    if (switchRaf) {
        cancelAnimationFrame(switchRaf);
    }
    document.documentElement.classList.remove('theme-switching');
});
</script>

<template>
    <div
        :class="[
            'relative grid h-screen min-h-0 overflow-hidden bg-[var(--bg-page)] text-[var(--text-primary)]',
            hideSidebar
                ? 'grid-cols-[1fr]'
                : 'grid-cols-[360px_1fr] max-[1120px]:grid-cols-[320px_1fr] max-[720px]:grid-cols-[1fr]'
        ]"
    >
        <div class="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_right,rgba(96,165,250,0.12),transparent_28%),radial-gradient(circle_at_bottom_left,rgba(59,130,246,0.08),transparent_32%)]"></div>
        <Sidebar v-if="!hideSidebar" />
        <div class="relative min-w-0 min-h-0 overflow-hidden">
            <div class="h-full min-h-0 overflow-y-auto">
                <RouterView />
            </div>
            <Toast />
        </div>
    </div>
    
    <!-- 全局悬浮小人 -->
    <Teleport to="body">
        <CompanionPet label="Agent" />
    </Teleport>
</template>
