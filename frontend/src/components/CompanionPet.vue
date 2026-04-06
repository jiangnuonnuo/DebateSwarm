<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';

const props = defineProps({
    label: {
        type: String,
        default: ''
    }
});

const rootRef = ref(null);
const pupilX = ref(0);
const pupilY = ref(0);
const blink = ref(false);
const reducedMotion = ref(false);

let targetX = 0;
let targetY = 0;
let currentX = 0;
let currentY = 0;
let rafId = 0;
let blinkTimer = null;
let reduceMotionQuery = null;

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

const scheduleFrame = () => {
    if (rafId) return;
    rafId = window.requestAnimationFrame(() => {
        rafId = 0;
        currentX += (targetX - currentX) * 0.18;
        currentY += (targetY - currentY) * 0.18;
        pupilX.value = Number(currentX.toFixed(2));
        pupilY.value = Number(currentY.toFixed(2));
        if (Math.abs(targetX - currentX) > 0.04 || Math.abs(targetY - currentY) > 0.04) {
            scheduleFrame();
        }
    });
};

const updateEyeTarget = (event) => {
    const root = rootRef.value;
    if (!root) return;
    const rect = root.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    const offsetX = (event.clientX - centerX) / Math.max(1, rect.width);
    const offsetY = (event.clientY - centerY) / Math.max(1, rect.height);
    targetX = clamp(offsetX * 14, -5.5, 5.5);
    targetY = clamp(offsetY * 14, -4, 4);
    scheduleFrame();
};

const clearBlinkTimer = () => {
    if (blinkTimer) {
        window.clearTimeout(blinkTimer);
        blinkTimer = null;
    }
};

const loopBlink = () => {
    clearBlinkTimer();
    if (reducedMotion.value) return;
    blinkTimer = window.setTimeout(() => {
        blink.value = true;
        window.setTimeout(() => {
            blink.value = false;
            loopBlink();
        }, 170);
    }, 2200 + Math.random() * 2600);
};

const syncReducedMotion = (matches) => {
    reducedMotion.value = matches;
    if (matches) {
        clearBlinkTimer();
        blink.value = false;
        return;
    }
    loopBlink();
};

const handleMotionPreference = (event) => {
    syncReducedMotion(Boolean(event.matches));
};

onMounted(() => {
    reduceMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    syncReducedMotion(reduceMotionQuery.matches);
    if (typeof reduceMotionQuery.addEventListener === 'function') {
        reduceMotionQuery.addEventListener('change', handleMotionPreference);
    } else if (typeof reduceMotionQuery.addListener === 'function') {
        reduceMotionQuery.addListener(handleMotionPreference);
    }
    window.addEventListener('mousemove', updateEyeTarget, { passive: true });
});

onBeforeUnmount(() => {
    window.removeEventListener('mousemove', updateEyeTarget);
    clearBlinkTimer();
    if (rafId) {
        window.cancelAnimationFrame(rafId);
    }
    if (reduceMotionQuery) {
        if (typeof reduceMotionQuery.removeEventListener === 'function') {
            reduceMotionQuery.removeEventListener('change', handleMotionPreference);
        } else if (typeof reduceMotionQuery.removeListener === 'function') {
            reduceMotionQuery.removeListener(handleMotionPreference);
        }
    }
});
</script>

<template>
    <div ref="rootRef" class="pet-card" :class="{ 'pet-card--reduced': reducedMotion }">
        <div class="pet-card__halo"></div>
        <svg
            class="pet-card__figure"
            :class="{ 'pet-card__figure--idle': !reducedMotion }"
            viewBox="0 0 160 132"
            role="img"
            aria-label="跟随鼠标视线的小宠物"
        >
            <defs>
                <linearGradient id="petBody" x1="0%" x2="100%" y1="0%" y2="100%">
                    <stop offset="0%" stop-color="#eef7ff" />
                    <stop offset="62%" stop-color="#d8eaff" />
                    <stop offset="100%" stop-color="#9cc8ff" />
                </linearGradient>
                <linearGradient id="petAccent" x1="0%" x2="100%" y1="0%" y2="100%">
                    <stop offset="0%" stop-color="#4ba7ff" />
                    <stop offset="100%" stop-color="#2f7cf6" />
                </linearGradient>
            </defs>

            <ellipse cx="80" cy="114" rx="44" ry="10" fill="rgba(47,124,246,0.16)" />
            <path d="M46 44c2-16 14-28 30-28 13 0 23 7 28 19 4-3 8-4 13-4 15 0 27 11 29 27 1 13-5 25-15 32-7 5-15 8-24 8H66c-23 0-41-18-38-41 1-5 3-9 6-13 3-4 7-7 12-9Z" fill="url(#petBody)" />
            <path d="M60 23c-1-9 5-17 14-19 10-3 20 2 24 11 1 3 2 6 1 9-6-6-14-9-23-9-6 0-11 1-16 4 0 1 0 3 0 4Z" fill="url(#petAccent)" opacity="0.88" />
            <path d="M40 52c-9-3-15-11-15-21 0-13 10-23 23-23 10 0 18 7 21 16-10 2-21 10-29 28Z" fill="url(#petAccent)" opacity="0.72" />
            <path d="M117 49c4-11 13-17 24-17 13 0 23 10 23 23 0 12-9 22-21 23 1-9-1-18-7-29-6-1-12-1-19 0Z" fill="url(#petAccent)" opacity="0.68" />
            <path d="M54 36c6-8 16-13 27-13 13 0 24 6 30 17" fill="none" stroke="rgba(255,255,255,0.64)" stroke-linecap="round" stroke-width="4" />
            <circle cx="63" cy="59" r="7" fill="rgba(255,255,255,0.72)" />
            <circle cx="109" cy="52" r="6" fill="rgba(255,255,255,0.6)" />
            <g transform="translate(0 2)">
                <g :class="{ 'pet-card__eye--blink': blink }">
                    <ellipse cx="63" cy="69" rx="16" ry="14" fill="#ffffff" />
                    <ellipse cx="97" cy="69" rx="16" ry="14" fill="#ffffff" />
                    <g :transform="`translate(${pupilX}, ${pupilY})`">
                        <circle cx="63" cy="69" r="6.2" fill="#163459" />
                        <circle cx="97" cy="69" r="6.2" fill="#163459" />
                        <circle cx="65.2" cy="66.8" r="1.5" fill="#ffffff" />
                        <circle cx="99.2" cy="66.8" r="1.5" fill="#ffffff" />
                    </g>
                </g>
                <path d="M77 79c1.5 1.5 4.5 1.5 6 0" fill="none" stroke="#3977c5" stroke-linecap="round" stroke-width="3" />
                <path d="M70 86c7 5 14 5 20 0" fill="none" stroke="#3977c5" stroke-linecap="round" stroke-width="3.5" />
                <ellipse cx="50" cy="82" rx="7" ry="4.4" fill="rgba(86,167,255,0.22)" />
                <ellipse cx="110" cy="82" rx="7" ry="4.4" fill="rgba(86,167,255,0.22)" />
            </g>
        </svg>
        <div v-if="props.label" class="pet-card__label">{{ props.label }}</div>
    </div>
</template>

<style scoped>
.pet-card {
    position: relative;
    display: inline-flex;
    min-width: 118px;
    align-items: center;
    justify-content: center;
    border-radius: 26px;
    padding: 10px 12px 8px;
    background:
        radial-gradient(circle at 20% 0%, rgba(255, 255, 255, 0.8), transparent 44%),
        linear-gradient(145deg, rgba(240, 248, 255, 0.96), rgba(210, 231, 255, 0.92));
    border: 1px solid rgba(87, 151, 240, 0.22);
    box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.75),
        0 16px 36px rgba(47, 124, 246, 0.14);
    overflow: hidden;
}

.pet-card__halo {
    position: absolute;
    inset: auto auto -16px 50%;
    width: 92px;
    height: 92px;
    transform: translateX(-50%);
    border-radius: 999px;
    background: radial-gradient(circle, rgba(47, 124, 246, 0.18), transparent 70%);
    pointer-events: none;
}

.pet-card__figure {
    position: relative;
    z-index: 1;
    width: 94px;
    height: 74px;
    filter: drop-shadow(0 8px 12px rgba(47, 124, 246, 0.18));
}

.pet-card__figure--idle {
    animation: pet-bob 4.8s ease-in-out infinite;
}

.pet-card__label {
    position: absolute;
    right: 10px;
    bottom: 10px;
    z-index: 2;
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.88);
    padding: 4px 8px;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #2f7cf6;
    box-shadow: 0 8px 20px rgba(47, 124, 246, 0.14);
}

.pet-card__eye--blink {
    transform-origin: center;
    animation: pet-blink 0.18s ease;
}

.pet-card--reduced .pet-card__figure,
.pet-card--reduced .pet-card__figure--idle {
    animation: none;
}

@keyframes pet-bob {
    0%,
    100% {
        transform: translateY(0px);
    }

    50% {
        transform: translateY(-4px);
    }
}

@keyframes pet-blink {
    0%,
    100% {
        transform: scaleY(1);
    }

    50% {
        transform: scaleY(0.14);
    }
}
</style>
