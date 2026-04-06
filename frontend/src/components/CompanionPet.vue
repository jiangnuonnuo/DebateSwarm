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

// 拖拽相关状态
const isDragging = ref(false);
const scale = ref(0.8); // 默认缩小一点
const position = ref({ x: window.innerWidth - 160, y: window.innerHeight - 180 });
const dragOffset = { x: 0, y: 0 };

let targetX = 0;
let targetY = 0;
let currentX = 0;
let currentY = 0;
let rafId = 0;
let blinkTimer = null;
let reduceMotionQuery = null;

const handleWheel = (event) => {
    event.preventDefault();
    const delta = event.deltaY > 0 ? -0.05 : 0.05;
    scale.value = Math.min(1.5, Math.max(0.4, scale.value + delta));
};

const resetScale = () => {
    scale.value = 0.8;
};

const startDrag = (event) => {
    isDragging.value = true;
    const rect = rootRef.value.getBoundingClientRect();
    dragOffset.x = event.clientX - rect.left;
    dragOffset.y = event.clientY - rect.top;
    window.addEventListener('mousemove', onDrag);
    window.addEventListener('mouseup', stopDrag);
};

const onDrag = (event) => {
    if (!isDragging.value) return;
    const x = event.clientX - dragOffset.x;
    const y = event.clientY - dragOffset.y;
    
    // 边界检查，考虑缩放后的尺寸
    const rect = rootRef.value.getBoundingClientRect();
    const maxX = window.innerWidth - rect.width;
    const maxY = window.innerHeight - rect.height;
    
    position.value = {
        x: Math.max(0, Math.min(x, window.innerWidth - rect.width)),
        y: Math.max(0, Math.min(y, window.innerHeight - rect.height))
    };
};

const stopDrag = () => {
    isDragging.value = false;
    window.removeEventListener('mousemove', onDrag);
    window.removeEventListener('mouseup', stopDrag);
};

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

const handleResize = () => {
    const maxX = window.innerWidth - 180;
    const maxY = window.innerHeight - 200;
    position.value.x = Math.min(position.value.x, maxX);
    position.value.y = Math.min(position.value.y, maxY);
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
    window.addEventListener('resize', handleResize);
    handleResize();
});

onBeforeUnmount(() => {
    window.removeEventListener('mousemove', updateEyeTarget);
    window.removeEventListener('resize', handleResize);
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
    <div 
        ref="rootRef" 
        class="pet-floating" 
        :class="{ 
            'pet-floating--reduced': reducedMotion,
            'pet-floating--active': props.label,
            'pet-floating--dragging': isDragging
        }"
        :style="{ left: `${position.x}px`, top: `${position.y}px` }"
        @mousedown="startDrag"
    >
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
                    <stop offset="0%" stop-color="#ffffff" />
                    <stop offset="62%" stop-color="#eff6ff" />
                    <stop offset="100%" stop-color="#bfdbfe" />
                </linearGradient>
                <linearGradient id="petAccent" x1="0%" x2="100%" y1="0%" y2="100%">
                    <stop offset="0%" stop-color="#60a5fa" />
                    <stop offset="100%" stop-color="#3b82f6" />
                </linearGradient>
            </defs>

            <ellipse cx="80" cy="114" rx="44" ry="10" fill="rgba(59,130,246,0.12)" />
            <path d="M46 44c2-16 14-28 30-28 13 0 23 7 28 19 4-3 8-4 13-4 15 0 27 11 29 27 1 13-5 25-15 32-7 5-15 8-24 8H66c-23 0-41-18-38-41 1-5 3-9 6-13 3-4 7-7 12-9Z" fill="url(#petBody)" />
            <path d="M60 23c-1-9 5-17 14-19 10-3 20 2 24 11 1 3 2 6 1 9-6-6-14-9-23-9-6 0-11 1-16 4 0 1 0 3 0 4Z" fill="url(#petAccent)" opacity="0.88" />
            <path d="M40 52c-9-3-15-11-15-21 0-13 10-23 23-23 10 0 18 7 21 16-10 2-21 10-29 28Z" fill="url(#petAccent)" opacity="0.72" />
            <path d="M117 49c4-11 13-17 24-17 13 0 23 10 23 23 0 12-9 22-21 23 1-9-1-18-7-29-6-1-12-1-19 0Z" fill="url(#petAccent)" opacity="0.68" />
            <path d="M54 36c6-8 16-13 27-13 13 0 24 6 30 17" fill="none" stroke="rgba(255,255,255,0.64)" stroke-linecap="round" stroke-width="4" />
            <circle cx="63" cy="59" r="7" fill="rgba(255,255,255,0.72)" />
            <circle cx="109" cy="52" r="6" fill="rgba(255,255,255,0.6)" />
            <g transform="translate(0 2)">
                <g :class="{ 'pet-card__eye--blink': blink }">
                    <ellipse cx="63" cy="69" rx="18" ry="16" fill="#ffffff" />
                    <ellipse cx="97" cy="69" rx="18" ry="16" fill="#ffffff" />
                    <g :transform="`translate(${pupilX}, ${pupilY})`">
                        <circle cx="63" cy="69" r="8.5" fill="#1d4ed8" />
                        <circle cx="97" cy="69" r="8.5" fill="#1d4ed8" />
                        <circle cx="66" cy="66" r="2.2" fill="#ffffff" />
                        <circle cx="100" cy="66" r="2.2" fill="#ffffff" />
                    </g>
                </g>
                <path d="M77 79c1.5 1.5 4.5 1.5 6 0" fill="none" stroke="#3b82f6" stroke-linecap="round" stroke-width="3" />
                <path d="M70 86c7 5 14 5 20 0" fill="none" stroke="#3b82f6" stroke-linecap="round" stroke-width="3.5" />
                <ellipse cx="50" cy="82" rx="7" ry="4.4" fill="rgba(59,130,246,0.15)" />
                <ellipse cx="110" cy="82" rx="7" ry="4.4" fill="rgba(59,130,246,0.15)" />
            </g>
        </svg>
        <div v-if="props.label" class="pet-card__label">{{ props.label }}</div>
    </div>
</template>

<style scoped>
.pet-floating {
    position: fixed;
    z-index: 9999;
    display: inline-flex;
    min-width: 180px;
    align-items: center;
    justify-content: center;
    border-radius: 40px;
    padding: 20px 24px 18px;
    background:
        radial-gradient(circle at 20% 0%, rgba(255, 255, 255, 0.9), transparent 44%),
        linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(239, 246, 255, 0.95));
    border: 1px solid rgba(59, 130, 246, 0.15);
    box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.8),
        0 24px 56px -12px rgba(59, 130, 246, 0.28);
    overflow: hidden;
    cursor: grab;
    transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.3s ease;
    backdrop-filter: blur(12px);
    user-select: none;
    touch-action: none;
}

.pet-floating--dragging {
    cursor: grabbing;
    transition: none;
    box-shadow: 0 32px 72px -12px rgba(59, 130, 246, 0.45);
    opacity: 0.9;
}

.pet-floating:hover:not(.pet-floating--dragging) {
    transform: scale(1.08) translateY(-5px);
    box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.8),
        0 32px 72px -12px rgba(59, 130, 246, 0.38);
}

.pet-card__halo {
    position: absolute;
    inset: auto auto -20px 50%;
    width: 140px;
    height: 140px;
    transform: translateX(-50%);
    border-radius: 999px;
    background: radial-gradient(circle, rgba(59, 130, 246, 0.15), transparent 70%);
    pointer-events: none;
}

.pet-card__figure {
    position: relative;
    z-index: 1;
    width: 140px;
    height: 110px;
    filter: drop-shadow(0 12px 24px rgba(59, 130, 246, 0.18));
}

.pet-card__figure--idle {
    animation: pet-float 4s ease-in-out infinite;
}

@keyframes pet-float {
    0%, 100% { transform: translateY(0) rotate(0); }
    25% { transform: translateY(-6px) rotate(1.5deg); }
    75% { transform: translateY(3px) rotate(-1.5deg); }
}

.pet-card__eye--blink {
    transform: scaleY(0.1);
    transform-origin: 80px 69px;
    transition: transform 0.1s ease-in-out;
}

.pet-card__label {
    position: absolute;
    top: 8px;
    right: 18px;
    font-size: 11px;
    font-weight: 800;
    letter-spacing: 0.05em;
    text-transform: uppercase;
    color: #2563eb;
    opacity: 0.6;
}

[data-theme='dark'] .pet-floating {
    background:
        radial-gradient(circle at 20% 0%, rgba(255, 255, 255, 0.05), transparent 44%),
        linear-gradient(145deg, rgba(30, 41, 59, 0.9), rgba(15, 23, 42, 0.95));
    border-color: rgba(255, 255, 255, 0.08);
    box-shadow: 0 32px 72px -12px rgba(0, 0, 0, 0.6);
}
</style>
