<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Footer from './Footer.vue';
import {
    detailXhsTask,
    pageXhsTasks,
    queryChatModels,
    submitXhsIntelligentTask
} from '../request/api';
import {
    normalizeError,
    notifyAppError,
    notifyAppSuccess
} from '../request/request';

const router = useRouter();
const route = useRoute();

const DEFAULT_PAGE_SIZE = 6;
const VITE_API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8066/miniagent';
const XHS_PUBLISH_BASE_PATH = '/api/v1/xhs/publish';
const XHS_ASSET_ACCESS_PATH = `${XHS_PUBLISH_BASE_PATH}/material/access`;
const API_BASE_URL = VITE_API_BASE.replace(/\/$/, '');
const API_BASE_PATH = (() => {
    if (API_BASE_URL.startsWith('http://') || API_BASE_URL.startsWith('https://')) {
        return new URL(API_BASE_URL).pathname.replace(/\/$/, '');
    }
    return API_BASE_URL.replace(/\/$/, '');
})();

const VISIBILITY_OPTIONS = [
    { label: '公开可见', value: '公开可见' },
    { label: '仅自己可见', value: '仅自己可见' },
    { label: '仅互关好友可见', value: '仅互关好友可见' }
];

const TASK_TEXT_MAP = {
    draft: '草稿',
    planning: '策划中',
    running: '执行中',
    image_generating: '补图中',
    param_building: '参数组装',
    publishing: '发布中',
    copy_review_pending: '文案待审核',
    pre_publish_review_pending: '发布前待审核',
    accepted: '已受理',
    published: '已发布',
    failed: '失败',
    cancelled: '已取消',
    pending: '待处理',
    approved: '已通过',
    rejected: '已拒绝',
    skipped: '已跳过',
    copy_review: '文案审核',
    pre_publish_review: '发布前审核',
    image: '图文',
    video: '视频',
    A: 'A 模式',
    B: 'B 模式'
};

const form = reactive({
    taskName: '',
    clientId: '',
    publishRequirement: '',
    bindingId: '',
    visibility: '公开可见',
    isOriginal: true,
    scheduledPublishAt: '',
    originImageUrlsText: ''
});

const pageState = reactive({
    pageNum: 1,
    pageSize: DEFAULT_PAGE_SIZE,
    total: 0,
    pageSum: 1,
    list: []
});

const clientLoading = ref(false);
const recordLoading = ref(false);
const detailLoading = ref(false);
const submitting = ref(false);

const clients = ref([]);
const selectedTask = ref(null);
const advancedOpen = ref(false);
const fileInputRef = ref(null);
const localFiles = ref([]);
const localPreviewUrls = ref([]);
const formError = ref('');
const recordError = ref('');
const detailError = ref('');
const lastSubmit = ref(null);

const selectedTaskId = computed(() => {
    const raw = Array.isArray(route.query.taskId) ? route.query.taskId[0] : route.query.taskId;
    return typeof raw === 'string' ? raw.trim() : '';
});
const selectedAttemptId = computed(() => {
    const raw = Array.isArray(route.query.attemptId) ? route.query.attemptId[0] : route.query.attemptId;
    return typeof raw === 'string' ? raw.trim() : '';
});

const remoteUrlList = computed(() => parseRemoteUrlList(form.originImageUrlsText));
const localImageCount = computed(() => localFiles.value.length);
const totalImageCount = computed(() => localFiles.value.length + remoteUrlList.value.length);
const hasSelectedTask = computed(() => Boolean(selectedTaskId.value));

const recentSnapshots = computed(() => pageState.list.slice(0, 3));
const heroSignals = computed(() => [
    { label: '当前视图', value: hasSelectedTask.value ? '详情回看' : '创作提交' },
    { label: '图像输入', value: `${totalImageCount.value} 张` },
    { label: 'Client 状态', value: form.clientId ? '已连接' : '待选择' }
]);
const heroDraftTitle = computed(() => {
    if (hasSelectedTask.value) {
        return selectedTask.value?.taskName || selectedTask.value?.taskId || '正在回看这条任务';
    }
    return form.taskName.trim() || '把下一篇要发布的灵感写进来';
});
const heroDraftNarrative = computed(() => {
    if (hasSelectedTask.value && selectedTask.value) {
        return `当前停留在 ${renderTokenText(selectedTask.value.currentStage || selectedTask.value.taskStatus)}，可以沿着时间线继续回看每一次流转。`;
    }
    if (form.publishRequirement.trim()) {
        return `发布需求已经写入 ${form.publishRequirement.trim().length} 个字，页面会跟着这段语气慢慢聚焦。`;
    }
    return '先写下标题、挑好 Client，再把这次要发布的语气和重点轻轻放进来。';
});
const rhythmNotes = computed(() => [
    {
        label: '发布节奏',
        value: form.scheduledPublishAt ? formatDateTime(normalizeLocalDateTime(form.scheduledPublishAt)) : '立即进入执行流'
    },
    {
        label: '可见范围',
        value: form.visibility || '公开可见'
    },
    {
        label: '素材结构',
        value: `${localImageCount.value} 张本地 / ${remoteUrlList.value.length} 张远程`
    }
]);
const latestTaskMoment = computed(() => {
    const task = recentSnapshots.value[0];
    if (!task) return null;
    return {
        taskId: task.taskId,
        title: task.taskName || task.taskId,
        status: renderTokenText(task.currentStage || task.taskStatus),
        time: formatDateTime(task.updateTime) || '刚刚更新'
    };
});

// 文艺感：用渐变标题、留白和少量衬线副标题组织层级；动态氛围则通过提交后状态切换与记录刷新来体现。
const heroMetrics = computed(() => {
    const list = pageState.list;
    const total = pageState.total || list.length;
    const activeCount = list.filter((item) => {
        const token = `${item.taskStatus || ''} ${item.currentStage || ''}`.toLowerCase();
        return token.includes('running') || token.includes('planning') || token.includes('publishing');
    }).length;
    const reviewCount = list.filter((item) => {
        const token = `${item.taskStatus || ''} ${item.currentStage || ''}`.toLowerCase();
        return token.includes('review') || token.includes('pending');
    }).length;
    const settledCount = list.filter((item) => {
        const token = `${item.taskStatus || ''}`.toLowerCase();
        return token.includes('accepted') || token.includes('published');
    }).length;

    return [
        { label: '任务存量', value: total, caption: '已经沉淀在发布台里的创作轨迹' },
        { label: '正在流转', value: activeCount, caption: '仍在执行树里继续推进的任务' },
        { label: '待审核/待确认', value: reviewCount, caption: '需要回看节点状态的任务数量' },
        { label: '已稳定落地', value: settledCount, caption: '已受理或已发布的结果回响' }
    ];
});

const latestSubmitSummary = computed(() => {
    if (lastSubmit.value) {
        return lastSubmit.value;
    }
    if (!selectedTask.value) {
        return null;
    }
    return {
        taskId: selectedTask.value.taskId,
        attemptId: selectedTask.value.attemptList?.[0]?.attemptId || '',
        taskName: selectedTask.value.taskName || '',
        clientId: parseClientIdFromTask(selectedTask.value),
        submittedAt: selectedTask.value.updateTime || selectedTask.value.createTime || ''
    };
});

const taskOverviewItems = computed(() => {
    const task = selectedTask.value;
    if (!task) return [];
    return [
        { label: '任务名称', value: task.taskName || '未命名任务' },
        { label: '发布模式', value: renderTokenText(task.publishMode) },
        { label: '发布类型', value: renderTokenText(task.publishType) },
        { label: '任务状态', value: renderTokenText(task.taskStatus) },
        { label: '当前阶段', value: renderTokenText(task.currentStage) },
        { label: '绑定账号', value: task.bindingId || '默认账号策略' },
        { label: '定时发布', value: formatDateTime(task.scheduledPublishAt) || '立即执行' },
        { label: '创建时间', value: formatDateTime(task.createTime) || '-' }
    ];
});

const finalResultLink = computed(() => extractResultLink(selectedTask.value?.finalResultJson));
const finalResultPreview = computed(() => {
    const payload = safeParseJson(selectedTask.value?.finalResultJson);
    if (!payload) {
        return selectedTask.value?.finalResultJson || '';
    }
    return JSON.stringify(payload, null, 2);
});

const latestContextPreview = computed(() => {
    const payload = safeParseJson(selectedTask.value?.latestContextJson);
    if (!payload) {
        return selectedTask.value?.latestContextJson || '';
    }
    return JSON.stringify(payload, null, 2);
});

const canSubmit = computed(() =>
    !submitting.value &&
    Boolean(form.taskName.trim()) &&
    Boolean(form.clientId.trim()) &&
    Boolean(form.publishRequirement.trim()) &&
    totalImageCount.value > 0
);

function pickData(resp, message = '操作失败') {
    if (resp && typeof resp === 'object' && Object.prototype.hasOwnProperty.call(resp, 'code')) {
        if (resp.code !== 200) {
            const err = new Error(resp.info || message);
            err.status = 500;
            throw err;
        }
        return resp.data;
    }
    return resp?.data ?? resp?.result ?? resp;
}

function renderTokenText(value) {
    const text = String(value || '').trim();
    if (!text) return '未设置';
    if (TASK_TEXT_MAP[text]) return TASK_TEXT_MAP[text];
    const normalized = text.toLowerCase();
    if (TASK_TEXT_MAP[normalized]) return TASK_TEXT_MAP[normalized];
    return text
        .replace(/_/g, ' ')
        .replace(/\b\w/g, (char) => char.toUpperCase());
}

function normalizeLocalDateTime(value) {
    const text = String(value || '').trim();
    if (!text) return '';
    return text.length === 16 ? `${text}:00` : text;
}

function formatDateTime(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return String(value);
    }
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function formatFileSize(size) {
    const value = Number(size || 0);
    if (!Number.isFinite(value) || value <= 0) return '0 B';
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
    return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function parseRemoteUrlList(value) {
    return Array.from(
        new Set(
            String(value || '')
                .split(/[\n,，;；]/)
                .map((item) => item.trim())
                .filter(Boolean)
        )
    );
}

function revokeLocalPreviews() {
    localPreviewUrls.value.forEach((url) => URL.revokeObjectURL(url));
    localPreviewUrls.value = [];
}

function resetForm({ preserveClientId = true } = {}) {
    const nextClientId = preserveClientId ? form.clientId : '';
    form.taskName = '';
    form.clientId = nextClientId;
    form.publishRequirement = '';
    form.bindingId = '';
    form.visibility = '公开可见';
    form.isOriginal = true;
    form.scheduledPublishAt = '';
    form.originImageUrlsText = '';
    formError.value = '';
    advancedOpen.value = false;
    revokeLocalPreviews();
    localFiles.value = [];
    if (fileInputRef.value) {
        fileInputRef.value.value = '';
    }
}

function buildApiUrl(path) {
    const text = String(path || '').trim();
    if (!text) return '';
    if (/^(https?:|data:|blob:)/i.test(text)) return text;
    if (API_BASE_URL && text.startsWith(API_BASE_URL)) return text;
    if (API_BASE_PATH && text.startsWith(`${API_BASE_PATH}/`)) return text;
    const normalized = text.startsWith('/') ? text : `/${text}`;
    return `${API_BASE_URL}${normalized}`;
}

function resolveAssetPreviewUrl(asset) {
    if (!asset) return '';
    if (asset.accessUrl) {
        return buildApiUrl(asset.accessUrl);
    }
    if (asset.assetId) {
        return `${API_BASE_URL}${XHS_ASSET_ACCESS_PATH}?assetId=${encodeURIComponent(asset.assetId)}`;
    }
    return '';
}

function safeParseJson(text) {
    if (!text || typeof text !== 'string') return null;
    try {
        return JSON.parse(text);
    } catch {
        return null;
    }
}

function walkForLink(payload) {
    if (!payload || typeof payload !== 'object') return '';
    const candidates = ['noteUrl', 'note_url', 'postUrl', 'post_url', 'url', 'link'];
    for (const key of candidates) {
        const value = payload[key];
        if (typeof value === 'string' && /^https?:\/\//i.test(value.trim())) {
            return value.trim();
        }
    }
    for (const value of Object.values(payload)) {
        if (Array.isArray(value)) {
            for (const item of value) {
                const hit = walkForLink(item);
                if (hit) return hit;
            }
            continue;
        }
        if (value && typeof value === 'object') {
            const hit = walkForLink(value);
            if (hit) return hit;
        }
    }
    return '';
}

function extractResultLink(finalResultJson) {
    const payload = safeParseJson(finalResultJson);
    if (!payload) return '';
    return walkForLink(payload);
}

function parseClientIdFromTask(task) {
    const context = safeParseJson(task?.latestContextJson);
    const ext = context?.ext;
    if (ext && typeof ext.clientId === 'string' && ext.clientId.trim()) {
        return ext.clientId.trim();
    }
    const request = safeParseJson(task?.requestJson);
    if (request && typeof request.clientId === 'string' && request.clientId.trim()) {
        return request.clientId.trim();
    }
    return '';
}

function resolveBadgeClass(value) {
    const token = String(value || '').toLowerCase();
    if (!token) {
        return 'border-[rgba(148,163,184,0.2)] bg-[rgba(148,163,184,0.08)] text-[var(--text-secondary)]';
    }
    if (
        token.includes('published') ||
        token.includes('accepted') ||
        token.includes('approved')
    ) {
        return 'border-[rgba(16,185,129,0.18)] bg-[rgba(16,185,129,0.1)] text-[#047857]';
    }
    if (
        token.includes('failed') ||
        token.includes('rejected') ||
        token.includes('cancelled')
    ) {
        return 'border-[rgba(244,63,94,0.18)] bg-[rgba(244,63,94,0.1)] text-[#be123c]';
    }
    if (
        token.includes('pending') ||
        token.includes('review') ||
        token.includes('planning')
    ) {
        return 'border-[rgba(217,119,6,0.18)] bg-[rgba(217,119,6,0.1)] text-[#b45309]';
    }
    return 'border-[rgba(59,130,246,0.18)] bg-[rgba(59,130,246,0.1)] text-[var(--accent-color)]';
}

function openFilePicker() {
    fileInputRef.value?.click();
}

function handleFileChange(event) {
    const fileList = Array.from(event?.target?.files || []);
    if (!fileList.length) {
        return;
    }

    revokeLocalPreviews();
    localFiles.value = fileList.map((file, index) => {
        const previewUrl = URL.createObjectURL(file);
        localPreviewUrls.value.push(previewUrl);
        return {
            id: `${file.name}-${file.lastModified}-${index}`,
            file,
            name: file.name,
            size: file.size,
            previewUrl
        };
    });
}

function removeLocalFile(targetId) {
    const matched = localFiles.value.find((item) => item.id === targetId);
    if (matched?.previewUrl) {
        URL.revokeObjectURL(matched.previewUrl);
        localPreviewUrls.value = localPreviewUrls.value.filter((url) => url !== matched.previewUrl);
    }
    localFiles.value = localFiles.value.filter((item) => item.id !== targetId);
    if (fileInputRef.value && localFiles.value.length === 0) {
        fileInputRef.value.value = '';
    }
}

async function fetchClients() {
    clientLoading.value = true;
    try {
        const resp = await queryChatModels();
        const list = pickData(resp, '获取发布 Client 失败') || [];
        const normalized = (Array.isArray(list) ? list : [])
            .map((item) => {
                if (!item || typeof item !== 'object') return null;
                const clientId = String(item.clientId || '').trim();
                if (!clientId) return null;
                const clientName = String(item.clientName || '').trim();
                const modelName = String(item.modelName || '').trim();
                return {
                    clientId,
                    clientName,
                    modelName,
                    label: [clientName, modelName, clientId].filter(Boolean).join(' / ')
                };
            })
            .filter(Boolean);
        clients.value = normalized;
        if (!form.clientId && normalized.length > 0) {
            form.clientId = normalized[0].clientId;
        }
    } catch (error) {
        notifyAppError(error, '获取发布 Client 失败');
    } finally {
        clientLoading.value = false;
    }
}

async function fetchTaskPage({ pageNum = pageState.pageNum } = {}) {
    recordLoading.value = true;
    recordError.value = '';
    try {
        const resp = await pageXhsTasks({
            pageNum,
            pageSize: pageState.pageSize
        });
        const data = pickData(resp, '获取任务记录失败') || {};
        pageState.pageNum = Number(data.pageNum || pageNum || 1);
        pageState.pageSize = Number(data.pageSize || pageState.pageSize || DEFAULT_PAGE_SIZE);
        pageState.total = Number(data.total || 0);
        pageState.pageSum = Number(data.pageSum || 1);
        pageState.list = Array.isArray(data.list) ? data.list : [];
    } catch (error) {
        recordError.value = normalizeError(error).message || '获取任务记录失败';
        notifyAppError(error, '获取任务记录失败');
    } finally {
        recordLoading.value = false;
    }
}

async function fetchTaskDetail(taskId) {
    if (!taskId) {
        selectedTask.value = null;
        detailError.value = '';
        return;
    }

    detailLoading.value = true;
    detailError.value = '';
    try {
        const resp = await detailXhsTask(taskId, { toast: false });
        selectedTask.value = pickData(resp, '获取任务详情失败');
    } catch (error) {
        selectedTask.value = null;
        detailError.value = normalizeError(error).message || '获取任务详情失败';
        notifyAppError(error, '获取任务详情失败');
    } finally {
        detailLoading.value = false;
    }
}

async function handleSubmit() {
    formError.value = '';

    if (!form.taskName.trim()) {
        formError.value = '请先写下这次创作任务的标题。';
        return;
    }
    if (!form.clientId.trim()) {
        formError.value = '请先选择一个可用的生成 Client。';
        return;
    }
    if (!form.publishRequirement.trim()) {
        formError.value = '请补充发布需求，让生成链路知道要写什么。';
        return;
    }
    if (totalImageCount.value <= 0) {
        formError.value = '请至少上传一张图片，或在高级设置里填写远程图片地址。';
        return;
    }

    const formData = new FormData();
    formData.append('taskName', form.taskName.trim());
    formData.append('clientId', form.clientId.trim());
    formData.append('publishRequirement', form.publishRequirement.trim());
    if (form.bindingId.trim()) {
        formData.append('bindingId', form.bindingId.trim());
    }
    if (form.visibility.trim()) {
        formData.append('visibility', form.visibility.trim());
    }
    formData.append('isOriginal', String(Boolean(form.isOriginal)));
    const scheduledPublishAt = normalizeLocalDateTime(form.scheduledPublishAt);
    if (scheduledPublishAt) {
        formData.append('scheduledPublishAt', scheduledPublishAt);
    }
    remoteUrlList.value.forEach((item) => formData.append('originImageUrls', item));
    localFiles.value.forEach((item) => formData.append('fileList', item.file));

    submitting.value = true;
    try {
        const resp = await submitXhsIntelligentTask(formData);
        const data = pickData(resp, '智能发布提交失败');
        lastSubmit.value = {
            taskId: data?.taskId || '',
            attemptId: data?.attemptId || '',
            taskName: form.taskName.trim(),
            clientId: form.clientId.trim(),
            submittedAt: Date.now()
        };

        notifyAppSuccess('小红书智能发布任务已进入执行流');
        resetForm();
        await fetchTaskPage({ pageNum: 1 });
        router.push({
            path: '/xhs/publish',
            query: {
                taskId: data?.taskId || '',
                attemptId: data?.attemptId || ''
            }
        });
    } catch (error) {
        formError.value = normalizeError(error).message || '智能发布提交失败';
        notifyAppError(error, '智能发布提交失败');
    } finally {
        submitting.value = false;
    }
}

function openTaskDetail(taskId) {
    if (!taskId) return;
    router.push({
        path: '/xhs/publish',
        query: { taskId }
    });
}

function returnToWorkbench() {
    router.push({ path: '/xhs/publish' });
}

function goPrevPage() {
    if (pageState.pageNum <= 1 || recordLoading.value) return;
    fetchTaskPage({ pageNum: pageState.pageNum - 1 });
}

function goNextPage() {
    if (pageState.pageNum >= pageState.pageSum || recordLoading.value) return;
    fetchTaskPage({ pageNum: pageState.pageNum + 1 });
}

watch(
    selectedTaskId,
    (taskId) => {
        fetchTaskDetail(taskId);
    },
    { immediate: true }
);

onMounted(async () => {
    await Promise.all([
        fetchClients(),
        fetchTaskPage({ pageNum: 1 })
    ]);
});

onBeforeUnmount(() => {
    revokeLocalPreviews();
});
</script>

<template>
    <section class="page-shell xhs-publish-shell">
        <div class="page-body">
            <div class="page-wrap !max-w-[1360px]">
                <!-- 文艺感：这里把留白、衬线点缀和渐变标题收进蓝色玻璃场景里，避免后台式堆卡片。 -->
                <section class="page-hero xhs-hero overflow-hidden px-[28px] py-[28px] max-[720px]:px-[18px] max-[720px]:py-[20px]">
                    <div class="hero-noise"></div>
                    <div class="hero-aura hero-aura--one"></div>
                    <div class="hero-aura hero-aura--two"></div>
                    <div class="relative z-[1] flex flex-col gap-[22px]">
                        <div class="hero-stage-grid">
                            <div class="hero-copy-panel">
                                <div class="section-kicker xhs-serif">
                                    <span class="inline-flex h-[8px] w-[8px] rounded-full bg-[rgba(125,211,252,0.92)] shadow-[0_0_16px_rgba(125,211,252,0.68)]"></span>
                                    XHS Intelligent Publishing
                                </div>
                                <h1 class="mt-[16px] bg-[linear-gradient(120deg,#1d4f7c_0%,#3a79aa_38%,#77b8dd_100%)] bg-clip-text text-[clamp(2.2rem,4vw,3.35rem)] font-[700] leading-[1.02] text-transparent">
                                    小红书智能创作平台发布台
                                </h1>
                                <p class="page-subtitle max-w-[760px]">
                                    让需求、图片与发布节奏在一片更清透的蓝色场域里汇合。它不是一张后台表格，
                                    而是一个会随着素材、状态和任务流轻轻起伏的创作工作台。
                                </p>

                                <div class="hero-signal-row">
                                    <span
                                        v-for="signal in heroSignals"
                                        :key="signal.label"
                                        class="hero-signal-chip"
                                    >
                                        <span class="text-[var(--text-muted)]">{{ signal.label }}</span>
                                        <strong>{{ signal.value }}</strong>
                                    </span>
                                </div>

                                <div class="hero-actions mt-[18px]">
                                    <button
                                        v-if="hasSelectedTask"
                                        type="button"
                                        class="hero-ghost-button"
                                        @click="returnToWorkbench"
                                    >
                                        返回创作台
                                    </button>
                                    <button
                                        type="button"
                                        class="hero-ghost-button"
                                        @click="fetchTaskPage({ pageNum: pageState.pageNum })"
                                    >
                                        刷新记录
                                    </button>
                                </div>
                            </div>

                            <div class="hero-orbit-panel">
                                <article class="hero-orbit-card hero-orbit-card--primary">
                                    <div class="flex items-center justify-between gap-[12px]">
                                        <div class="section-kicker xhs-serif !tracking-[0.18em]">Flow Pulse</div>
                                        <span
                                            class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                            :class="resolveBadgeClass(selectedTask?.currentStage || selectedTask?.taskStatus || 'draft')"
                                        >
                                            {{ hasSelectedTask ? '详情联动中' : '准备提交' }}
                                        </span>
                                    </div>
                                    <div class="hero-orbit-title">
                                        {{ heroDraftTitle }}
                                    </div>
                                    <div class="hero-orbit-text">
                                        {{ heroDraftNarrative }}
                                    </div>
                                    <div class="hero-mini-grid">
                                        <article
                                            v-for="note in rhythmNotes"
                                            :key="note.label"
                                            class="hero-note-card"
                                        >
                                            <div class="hero-note-label">{{ note.label }}</div>
                                            <div class="hero-note-value">{{ note.value }}</div>
                                        </article>
                                    </div>
                                </article>

                                <article class="hero-orbit-card hero-orbit-card--secondary">
                                    <div class="section-kicker">Recent Echo</div>
                                    <div class="mt-[10px] text-[20px] font-semibold text-[var(--text-primary)]">
                                        最近一次提交摘要
                                    </div>
                                    <template v-if="latestSubmitSummary">
                                        <div class="mt-[14px] flex flex-col gap-[10px]">
                                            <div class="hero-echo-line">
                                                <span>Task</span>
                                                <strong>{{ latestSubmitSummary.taskName || latestSubmitSummary.taskId }}</strong>
                                            </div>
                                            <div class="hero-echo-line">
                                                <span>Attempt</span>
                                                <strong>{{ latestSubmitSummary.attemptId || '等待生成 attempt' }}</strong>
                                            </div>
                                            <div class="hero-echo-line">
                                                <span>写入时间</span>
                                                <strong>{{ formatDateTime(latestSubmitSummary.submittedAt) || '刚刚写入执行流' }}</strong>
                                            </div>
                                        </div>
                                    </template>
                                    <div v-else class="hero-empty-note mt-[14px]">
                                        第一条任务提交成功后，这里会轻轻留下最新的 taskId 与 attemptId。
                                    </div>
                                </article>
                            </div>
                        </div>

                        <div class="hero-lower-grid">
                            <div class="stat-grid xhs-stat-grid">
                                <article
                                    v-for="(metric, index) in heroMetrics"
                                    :key="metric.label"
                                    :class="['stat-card xhs-stat-card', `xhs-stat-card--${index + 1}`]"
                                >
                                    <div class="stat-label">{{ metric.label }}</div>
                                    <div class="stat-value bg-[linear-gradient(120deg,#2c6b98_0%,#5b95c5_45%,#8bc5e6_100%)] bg-clip-text text-transparent">
                                        {{ metric.value }}
                                    </div>
                                    <div class="mt-[10px] text-[12px] leading-[1.7] text-[var(--text-secondary)]">
                                        {{ metric.caption }}
                                    </div>
                                </article>
                            </div>

                            <div class="context-panel hero-snapshot-panel p-[18px]">
                                <div class="flex items-center justify-between gap-[12px]">
                                    <div>
                                        <div class="section-kicker">Soft Snapshots</div>
                                        <div class="mt-[8px] text-[20px] font-semibold text-[var(--text-primary)]">
                                            最近任务快照
                                        </div>
                                    </div>
                                    <span class="toolbar-chip xhs-toolbar-chip">
                                        {{ recentSnapshots.length || 0 }} 条浮动回声
                                    </span>
                                </div>

                                <div v-if="recentSnapshots.length" class="mt-[16px] flex flex-col gap-[10px]">
                                    <button
                                        v-for="task in recentSnapshots"
                                        :key="task.taskId"
                                        type="button"
                                        class="snapshot-button snapshot-button--airy"
                                        @click="openTaskDetail(task.taskId)"
                                    >
                                        <div class="min-w-0">
                                            <div class="truncate text-[15px] font-semibold text-[var(--text-primary)]">
                                                {{ task.taskName || task.taskId }}
                                            </div>
                                            <div class="mt-[5px] text-[12px] text-[var(--text-secondary)]">
                                                {{ formatDateTime(task.updateTime) || '刚刚更新' }}
                                            </div>
                                        </div>
                                        <span
                                            class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                            :class="resolveBadgeClass(task.currentStage || task.taskStatus)"
                                        >
                                            {{ renderTokenText(task.currentStage || task.taskStatus) }}
                                        </span>
                                    </button>
                                </div>
                                <div v-else class="hero-empty-note mt-[16px]">
                                    任务列表一旦回来，最新的状态会先在这里泛起一圈浅蓝色的回声。
                                </div>
                            </div>
                        </div>
                    </div>
                </section>

                <!-- 轻量未来感：主工作区使用毛玻璃与柔和辉光，而不是厚重后台卡片。 -->
                <div class="workspace-grid mt-[22px] items-start">
                    <main class="workspace-stack min-h-0">
                        <section v-if="!hasSelectedTask" class="panel-surface xhs-workbench-panel overflow-hidden rounded-[30px] p-[22px] max-[720px]:p-[14px]">
                            <div class="section-title-row workbench-heading">
                                <div>
                                    <div class="section-kicker">Creative Workbench</div>
                                    <h2 class="mt-[8px] text-[26px] font-semibold text-[var(--text-primary)]">
                                        一键智能发布
                                    </h2>
                                    <p class="mt-[8px] max-w-[560px] text-[13px] leading-[1.8] text-[var(--text-secondary)]">
                                        左边把任务写得更清楚，右边让图片与节奏自己发光；页面会随着你的输入慢慢形成发布姿态。
                                    </p>
                                </div>
                                <div class="workbench-heading__chips">
                                    <span class="toolbar-chip xhs-toolbar-chip">
                                        共 {{ totalImageCount }} 张图进入待发队列
                                    </span>
                                    <span class="toolbar-chip xhs-toolbar-chip">
                                        {{ form.scheduledPublishAt ? '已设定发布时间' : '默认立即执行' }}
                                    </span>
                                </div>
                            </div>

                            <div class="publish-stage-grid mt-[22px]">
                                <div class="context-card form-sheet p-[20px]">
                                    <div class="grid gap-[16px]">
                                        <div>
                                            <label class="mb-[8px] block text-[13px] font-semibold text-[var(--text-primary)]">
                                                任务名称
                                            </label>
                                            <input
                                                v-model="form.taskName"
                                                type="text"
                                                maxlength="80"
                                                class="xhs-input"
                                                placeholder="例如：五一露营穿搭图文发布"
                                            />
                                        </div>

                                        <div>
                                            <label class="mb-[8px] block text-[13px] font-semibold text-[var(--text-primary)]">
                                                生成 Client
                                            </label>
                                            <select
                                                v-model="form.clientId"
                                                class="xhs-input"
                                                :disabled="clientLoading"
                                            >
                                                <option value="" disabled>
                                                    {{ clientLoading ? '加载中...' : '请选择一个可用 Client' }}
                                                </option>
                                                <option
                                                    v-for="client in clients"
                                                    :key="client.clientId"
                                                    :value="client.clientId"
                                                >
                                                    {{ client.label }}
                                                </option>
                                            </select>
                                        </div>

                                        <div>
                                            <label class="mb-[8px] block text-[13px] font-semibold text-[var(--text-primary)]">
                                                发布需求
                                            </label>
                                            <textarea
                                                v-model="form.publishRequirement"
                                                rows="8"
                                                class="xhs-textarea"
                                                placeholder="把这次希望生成的文案风格、目标人群、卖点、语气、是否带标签等写得尽量清楚。"
                                            ></textarea>
                                        </div>

                                        <button
                                            type="button"
                                            class="advanced-toggle"
                                            @click="advancedOpen = !advancedOpen"
                                        >
                                            <span>高级设置</span>
                                            <span class="text-[16px] text-[var(--text-secondary)]">
                                                {{ advancedOpen ? '－' : '＋' }}
                                            </span>
                                        </button>

                                        <transition name="soft-fade">
                                            <div v-if="advancedOpen" class="advanced-sheet">
                                                <div class="grid gap-[16px] md:grid-cols-2">
                                                    <div>
                                                        <label class="mb-[8px] block text-[13px] font-semibold text-[var(--text-primary)]">
                                                            bindingId
                                                        </label>
                                                        <input
                                                            v-model="form.bindingId"
                                                            type="text"
                                                            class="xhs-input"
                                                            placeholder="可选，留空则走默认账号"
                                                        />
                                                    </div>
                                                    <div>
                                                        <label class="mb-[8px] block text-[13px] font-semibold text-[var(--text-primary)]">
                                                            可见性
                                                        </label>
                                                        <select v-model="form.visibility" class="xhs-input">
                                                            <option
                                                                v-for="option in VISIBILITY_OPTIONS"
                                                                :key="option.value"
                                                                :value="option.value"
                                                            >
                                                                {{ option.label }}
                                                            </option>
                                                        </select>
                                                    </div>
                                                </div>

                                                <div class="grid gap-[16px] md:grid-cols-2">
                                                    <div>
                                                        <label class="mb-[8px] block text-[13px] font-semibold text-[var(--text-primary)]">
                                                            定时发布时间
                                                        </label>
                                                        <input
                                                            v-model="form.scheduledPublishAt"
                                                            type="datetime-local"
                                                            class="xhs-input"
                                                        />
                                                    </div>
                                                    <div>
                                                        <label class="mb-[8px] block text-[13px] font-semibold text-[var(--text-primary)]">
                                                            原创声明
                                                        </label>
                                                        <div class="segmented-tabs w-full">
                                                            <button
                                                                type="button"
                                                                class="segmented-tab flex-1"
                                                                :class="form.isOriginal ? 'segmented-tab--active' : ''"
                                                                @click="form.isOriginal = true"
                                                            >
                                                                原创
                                                            </button>
                                                            <button
                                                                type="button"
                                                                class="segmented-tab flex-1"
                                                                :class="!form.isOriginal ? 'segmented-tab--active' : ''"
                                                                @click="form.isOriginal = false"
                                                            >
                                                                非原创
                                                            </button>
                                                        </div>
                                                    </div>
                                                </div>

                                                <div>
                                                    <label class="mb-[8px] block text-[13px] font-semibold text-[var(--text-primary)]">
                                                        远程图片 URL
                                                    </label>
                                                    <textarea
                                                        v-model="form.originImageUrlsText"
                                                        rows="4"
                                                        class="xhs-textarea"
                                                        placeholder="每行一条，或用中英文逗号分隔。"
                                                    ></textarea>
                                                    <div v-if="remoteUrlList.length" class="mt-[10px] flex flex-wrap gap-[8px]">
                                                        <span
                                                            v-for="item in remoteUrlList"
                                                            :key="item"
                                                            class="rounded-full border border-[rgba(96,165,250,0.18)] bg-[rgba(96,165,250,0.08)] px-[10px] py-[6px] text-[11px] font-medium text-[var(--accent-color)]"
                                                        >
                                                            {{ item }}
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>
                                        </transition>

                                        <div v-if="formError" class="danger-inline">
                                            {{ formError }}
                                        </div>

                                        <div class="flex flex-wrap items-center justify-end gap-[10px]">
                                            <button
                                                type="button"
                                                class="hero-ghost-button"
                                                @click="resetForm()"
                                            >
                                                清空表单
                                            </button>
                                            <button
                                                type="button"
                                                class="xhs-primary-button"
                                                :disabled="!canSubmit"
                                                @click="handleSubmit"
                                            >
                                                {{ submitting ? '正在送入执行流...' : '提交智能发布' }}
                                            </button>
                                        </div>
                                    </div>
                                </div>

                                <!-- 动态氛围：图片区、节奏卡片和最近任务会随输入与记录变化，形成非机械式刷新反馈。 -->
                                <div class="publish-side-stack">
                                    <div class="context-card upload-gallery-card p-[18px]">
                                        <div class="flex items-center justify-between gap-[12px]">
                                            <div>
                                                <div class="text-[13px] font-semibold uppercase tracking-[0.18em] text-[var(--text-muted)]">
                                                    Image Sources
                                                </div>
                                                <div class="mt-[6px] text-[20px] font-semibold text-[var(--text-primary)]">
                                                    素材图片区
                                                </div>
                                            </div>
                                            <span class="toolbar-chip xhs-toolbar-chip">
                                                本地 {{ localImageCount }} / 远程 {{ remoteUrlList.length }}
                                            </span>
                                        </div>

                                        <div class="upload-dropzone mt-[18px]">
                                            <input
                                                ref="fileInputRef"
                                                type="file"
                                                accept="image/*"
                                                multiple
                                                class="hidden"
                                                @change="handleFileChange"
                                            />
                                            <div class="flex flex-wrap items-center justify-between gap-[12px]">
                                                <div>
                                                    <div class="text-[16px] font-semibold text-[var(--text-primary)]">
                                                        上传要发布的图片
                                                    </div>
                                                    <div class="mt-[6px] text-[13px] leading-[1.7] text-[var(--text-secondary)]">
                                                        支持一次放入多张图，也可以只在高级区填远程图片地址。
                                                    </div>
                                                </div>
                                                <button
                                                    type="button"
                                                    class="xhs-upload-button"
                                                    @click="openFilePicker"
                                                >
                                                    选择图片
                                                </button>
                                            </div>
                                        </div>

                                        <div v-if="localFiles.length" class="mt-[18px] grid gap-[12px] sm:grid-cols-2">
                                            <article
                                                v-for="item in localFiles"
                                                :key="item.id"
                                                class="upload-card"
                                            >
                                                <div class="overflow-hidden rounded-[18px] bg-[rgba(226,232,240,0.46)]">
                                                    <img
                                                        :src="item.previewUrl"
                                                        :alt="item.name"
                                                        class="h-[164px] w-full object-cover transition-transform duration-300 hover:scale-[1.04]"
                                                    />
                                                </div>
                                                <div class="mt-[12px] flex items-start justify-between gap-[12px]">
                                                    <div class="min-w-0">
                                                        <div class="truncate text-[14px] font-semibold text-[var(--text-primary)]">
                                                            {{ item.name }}
                                                        </div>
                                                        <div class="mt-[5px] text-[12px] text-[var(--text-secondary)]">
                                                            {{ formatFileSize(item.size) }}
                                                        </div>
                                                    </div>
                                                    <button
                                                        type="button"
                                                        class="xhs-remove-button"
                                                        @click="removeLocalFile(item.id)"
                                                    >
                                                        移除
                                                    </button>
                                                </div>
                                            </article>
                                        </div>
                                        <div v-else class="upload-empty-note mt-[18px]">
                                            图片会在这里形成一条轻一点的预览廊道，而不是冷冰冰的文件表格。
                                        </div>
                                    </div>

                                    <div class="context-card pulse-summary-card p-[18px]">
                                        <div class="flex items-start justify-between gap-[12px]">
                                            <div>
                                                <div class="section-kicker">Creative Pulse</div>
                                                <div class="mt-[8px] text-[20px] font-semibold text-[var(--text-primary)]">
                                                    发布节奏摘要
                                                </div>
                                            </div>
                                            <span
                                                class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                                :class="resolveBadgeClass(canSubmit ? 'accepted' : 'planning')"
                                            >
                                                {{ canSubmit ? '可提交' : '继续补全' }}
                                            </span>
                                        </div>

                                        <p class="mt-[14px] text-[13px] leading-[1.8] text-[var(--text-secondary)]">
                                            {{ canSubmit ? '这组需求与图片已经可以被送进执行流，状态刷新会以更柔和的方式浮现。' : '再补一点标题、素材或需求细节，这个页面就会慢慢完成它的发布姿态。' }}
                                        </p>

                                        <div class="pulse-chip-grid mt-[16px]">
                                            <article
                                                v-for="note in rhythmNotes"
                                                :key="note.label"
                                                class="pulse-chip-item"
                                            >
                                                <div class="pulse-chip-label">{{ note.label }}</div>
                                                <div class="pulse-chip-value">{{ note.value }}</div>
                                            </article>
                                        </div>
                                    </div>

                                    <div v-if="latestTaskMoment" class="context-card quick-return-card p-[18px]">
                                        <div class="section-kicker">Quick Return</div>
                                        <div class="mt-[8px] text-[18px] font-semibold text-[var(--text-primary)]">
                                            {{ latestTaskMoment.title }}
                                        </div>
                                        <div class="mt-[8px] text-[13px] leading-[1.7] text-[var(--text-secondary)]">
                                            {{ latestTaskMoment.status }} · {{ latestTaskMoment.time }}
                                        </div>
                                        <button
                                            type="button"
                                            class="hero-ghost-button mt-[16px] !min-h-[40px] !rounded-[14px]"
                                            @click="openTaskDetail(latestTaskMoment.taskId)"
                                        >
                                            打开最近任务
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <section v-else class="panel-surface overflow-hidden rounded-[28px] p-[20px] max-[720px]:p-[14px]">
                            <div class="section-title-row">
                                <div>
                                    <div class="section-kicker">Detail View</div>
                                    <h2 class="mt-[8px] text-[24px] font-semibold text-[var(--text-primary)]">
                                        任务详情
                                    </h2>
                                </div>
                                <div class="flex flex-wrap items-center gap-[10px]">
                                    <span
                                        v-if="selectedTask"
                                        class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                        :class="resolveBadgeClass(selectedTask.currentStage || selectedTask.taskStatus)"
                                    >
                                        {{ renderTokenText(selectedTask.currentStage || selectedTask.taskStatus) }}
                                    </span>
                                    <button type="button" class="hero-ghost-button" @click="returnToWorkbench">
                                        返回创作表单
                                    </button>
                                </div>
                            </div>

                            <div v-if="detailLoading" class="empty-state mt-[18px] min-h-[220px]">
                                正在沿着任务 ID 把细节一层层翻出来……
                            </div>
                            <div v-else-if="detailError" class="danger-inline mt-[18px]">
                                {{ detailError }}
                            </div>
                            <div v-else-if="selectedTask" class="mt-[18px] flex flex-col gap-[18px]">
                                <section class="context-card p-[18px]">
                                    <div class="flex flex-wrap items-start justify-between gap-[18px]">
                                        <div class="max-w-[720px]">
                                            <div class="xhs-serif text-[13px] tracking-[0.16em] text-[var(--text-muted)]">
                                                TASK OVERVIEW
                                            </div>
                                            <div class="mt-[8px] text-[24px] font-semibold text-[var(--text-primary)]">
                                                {{ selectedTask.taskName || selectedTask.taskId }}
                                            </div>
                                            <div class="mt-[10px] flex flex-wrap gap-[8px]">
                                                <span
                                                    class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                                    :class="resolveBadgeClass(selectedTask.taskStatus)"
                                                >
                                                    {{ renderTokenText(selectedTask.taskStatus) }}
                                                </span>
                                                <span
                                                    class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                                    :class="resolveBadgeClass(selectedTask.currentStage)"
                                                >
                                                    {{ renderTokenText(selectedTask.currentStage) }}
                                                </span>
                                                <span class="rounded-full border border-[rgba(96,165,250,0.18)] bg-[rgba(96,165,250,0.08)] px-[10px] py-[6px] text-[11px] font-semibold text-[var(--accent-color)]">
                                                    {{ selectedTask.taskId }}
                                                </span>
                                            </div>
                                        </div>

                                        <div class="toolbar-chip">
                                            Attempt
                                            <strong>{{ selectedAttemptId || selectedTask.attemptList?.[0]?.attemptId || '等待写入' }}</strong>
                                        </div>
                                    </div>

                                    <div class="mt-[18px] grid gap-[12px] md:grid-cols-2 xl:grid-cols-4">
                                        <div
                                            v-for="item in taskOverviewItems"
                                            :key="item.label"
                                            class="rounded-[18px] border border-[rgba(148,163,184,0.12)] bg-[rgba(255,255,255,0.72)] p-[14px]"
                                        >
                                            <div class="text-[12px] uppercase tracking-[0.14em] text-[var(--text-muted)]">
                                                {{ item.label }}
                                            </div>
                                            <div class="mt-[8px] text-[15px] font-semibold leading-[1.6] text-[var(--text-primary)]">
                                                {{ item.value }}
                                            </div>
                                        </div>
                                    </div>

                                    <div class="mt-[18px] grid gap-[18px] xl:grid-cols-[1fr_1fr]">
                                        <div class="rounded-[20px] border border-[rgba(148,163,184,0.12)] bg-[rgba(248,250,252,0.76)] p-[16px]">
                                            <div class="flex items-center justify-between gap-[12px]">
                                                <div class="text-[15px] font-semibold text-[var(--text-primary)]">
                                                    上下文快照
                                                </div>
                                                <span class="text-[12px] text-[var(--text-secondary)]">latestContextJson</span>
                                            </div>
                                            <pre class="xhs-code-block">{{ latestContextPreview || '暂无上下文快照' }}</pre>
                                        </div>

                                        <div class="rounded-[20px] border border-[rgba(148,163,184,0.12)] bg-[rgba(248,250,252,0.76)] p-[16px]">
                                            <div class="flex items-center justify-between gap-[12px]">
                                                <div class="text-[15px] font-semibold text-[var(--text-primary)]">
                                                    结果回响
                                                </div>
                                                <a
                                                    v-if="finalResultLink"
                                                    :href="finalResultLink"
                                                    target="_blank"
                                                    rel="noreferrer"
                                                    class="text-[12px] font-semibold text-[var(--accent-color)] underline underline-offset-4"
                                                >
                                                    打开帖子链接
                                                </a>
                                                <span v-else class="text-[12px] text-[var(--text-secondary)]">
                                                    暂无帖子链接
                                                </span>
                                            </div>
                                            <pre class="xhs-code-block">{{ finalResultPreview || '后端暂未返回结构化结果。' }}</pre>
                                        </div>
                                    </div>
                                </section>

                                <section class="context-card p-[18px]">
                                    <div class="section-title-row">
                                        <div>
                                            <div class="section-kicker">Attempt Timeline</div>
                                            <div class="mt-[8px] text-[20px] font-semibold text-[var(--text-primary)]">
                                                尝试时间线
                                            </div>
                                        </div>
                                        <span class="toolbar-chip">
                                            {{ selectedTask.attemptList?.length || 0 }} 次尝试
                                        </span>
                                    </div>

                                    <div v-if="selectedTask.attemptList?.length" class="mt-[18px] grid gap-[12px]">
                                        <article
                                            v-for="attempt in selectedTask.attemptList"
                                            :key="attempt.attemptId"
                                            class="timeline-card"
                                        >
                                            <div class="flex flex-wrap items-start justify-between gap-[12px]">
                                                <div>
                                                    <div class="text-[12px] uppercase tracking-[0.16em] text-[var(--text-muted)]">
                                                        Attempt {{ attempt.attemptNo || '-' }}
                                                    </div>
                                                    <div class="mt-[6px] text-[18px] font-semibold text-[var(--text-primary)]">
                                                        {{ attempt.attemptId }}
                                                    </div>
                                                </div>
                                                <div class="flex flex-wrap gap-[8px]">
                                                    <span
                                                        class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                                        :class="resolveBadgeClass(attempt.attemptStatus)"
                                                    >
                                                        {{ renderTokenText(attempt.attemptStatus) }}
                                                    </span>
                                                    <span
                                                        class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                                        :class="resolveBadgeClass(attempt.stage)"
                                                    >
                                                        {{ renderTokenText(attempt.stage) }}
                                                    </span>
                                                </div>
                                            </div>

                                            <div class="mt-[14px] grid gap-[10px] md:grid-cols-2 xl:grid-cols-4">
                                                <div class="timeline-meta">
                                                    <span>开始时间</span>
                                                    <strong>{{ formatDateTime(attempt.startTime) || '-' }}</strong>
                                                </div>
                                                <div class="timeline-meta">
                                                    <span>结束时间</span>
                                                    <strong>{{ formatDateTime(attempt.endTime) || '-' }}</strong>
                                                </div>
                                                <div class="timeline-meta">
                                                    <span>错误码</span>
                                                    <strong>{{ attempt.errorCode || '—' }}</strong>
                                                </div>
                                                <div class="timeline-meta">
                                                    <span>成本</span>
                                                    <strong>{{ attempt.costAmount ?? '—' }}</strong>
                                                </div>
                                            </div>

                                            <div
                                                v-if="attempt.errorMessage"
                                                class="mt-[12px] rounded-[16px] border border-[rgba(244,63,94,0.14)] bg-[rgba(244,63,94,0.08)] px-[14px] py-[12px] text-[13px] leading-[1.7] text-[#9f1239]"
                                            >
                                                {{ attempt.errorMessage }}
                                            </div>
                                        </article>
                                    </div>
                                    <div v-else class="empty-state mt-[18px] min-h-[180px]">
                                        当前任务还没有 attempt 时间线。
                                    </div>
                                </section>

                                <section class="grid gap-[18px] xl:grid-cols-[0.96fr_1.04fr]">
                                    <div class="context-card p-[18px]">
                                        <div class="section-title-row">
                                            <div>
                                                <div class="section-kicker">Review Records</div>
                                                <div class="mt-[8px] text-[20px] font-semibold text-[var(--text-primary)]">
                                                    审核记录
                                                </div>
                                            </div>
                                            <span class="toolbar-chip">
                                                {{ selectedTask.reviewList?.length || 0 }} 条
                                            </span>
                                        </div>

                                        <div v-if="selectedTask.reviewList?.length" class="mt-[18px] flex flex-col gap-[10px]">
                                            <article
                                                v-for="review in selectedTask.reviewList"
                                                :key="review.reviewId"
                                                class="rounded-[18px] border border-[rgba(148,163,184,0.12)] bg-[rgba(255,255,255,0.72)] p-[14px]"
                                            >
                                                <div class="flex flex-wrap items-start justify-between gap-[10px]">
                                                    <div>
                                                        <div class="text-[12px] uppercase tracking-[0.16em] text-[var(--text-muted)]">
                                                            {{ renderTokenText(review.reviewStage) }}
                                                        </div>
                                                        <div class="mt-[6px] text-[15px] font-semibold text-[var(--text-primary)]">
                                                            {{ review.reviewId }}
                                                        </div>
                                                    </div>
                                                    <span
                                                        class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                                        :class="resolveBadgeClass(review.reviewStatus)"
                                                    >
                                                        {{ renderTokenText(review.reviewStatus) }}
                                                    </span>
                                                </div>
                                                <div class="mt-[10px] text-[13px] leading-[1.7] text-[var(--text-secondary)]">
                                                    {{ review.reviewComment || '暂无审核备注' }}
                                                </div>
                                                <div class="mt-[10px] text-[12px] text-[var(--text-muted)]">
                                                    {{ formatDateTime(review.decidedTime) || '等待决策时间' }}
                                                </div>
                                            </article>
                                        </div>
                                        <div v-else class="empty-state mt-[18px] min-h-[180px]">
                                            当前任务还没有人工审核或系统审核回写。
                                        </div>
                                    </div>

                                    <div class="context-card p-[18px]">
                                        <div class="section-title-row">
                                            <div>
                                                <div class="section-kicker">Asset Gallery</div>
                                                <div class="mt-[8px] text-[20px] font-semibold text-[var(--text-primary)]">
                                                    素材列表
                                                </div>
                                            </div>
                                            <span class="toolbar-chip">
                                                {{ selectedTask.assetList?.length || 0 }} 份素材
                                            </span>
                                        </div>

                                        <div v-if="selectedTask.assetList?.length" class="mt-[18px] grid gap-[12px] sm:grid-cols-2">
                                            <article
                                                v-for="asset in selectedTask.assetList"
                                                :key="asset.assetId"
                                                class="upload-card"
                                            >
                                                <a
                                                    :href="resolveAssetPreviewUrl(asset) || '#'"
                                                    target="_blank"
                                                    rel="noreferrer"
                                                    class="block overflow-hidden rounded-[18px] bg-[rgba(226,232,240,0.46)]"
                                                >
                                                    <img
                                                        v-if="resolveAssetPreviewUrl(asset)"
                                                        :src="resolveAssetPreviewUrl(asset)"
                                                        :alt="asset.assetId"
                                                        class="h-[150px] w-full object-cover transition-transform duration-300 hover:scale-[1.04]"
                                                    />
                                                    <div
                                                        v-else
                                                        class="grid h-[150px] place-items-center text-[13px] text-[var(--text-secondary)]"
                                                    >
                                                        无可预览地址
                                                    </div>
                                                </a>
                                                <div class="mt-[12px] flex flex-col gap-[6px] text-[13px] text-[var(--text-secondary)]">
                                                    <div class="font-semibold text-[var(--text-primary)]">
                                                        {{ asset.assetId }}
                                                    </div>
                                                    <div>类型：{{ renderTokenText(asset.assetType) }}</div>
                                                    <div>来源：{{ renderTokenText(asset.sourceType) }}</div>
                                                    <div>状态：{{ renderTokenText(asset.assetStatus) }}</div>
                                                    <div v-if="asset.expireTime">
                                                        过期：{{ formatDateTime(asset.expireTime) }}
                                                    </div>
                                                </div>
                                            </article>
                                        </div>
                                        <div v-else class="empty-state mt-[18px] min-h-[180px]">
                                            当前任务还没有返回素材列表。
                                        </div>
                                    </div>
                                </section>
                            </div>
                        </section>
                    </main>

                    <aside class="workspace-stage xhs-record-stage">
                        <section class="conversation-side-card aside-story-card">
                            <div class="section-kicker xhs-serif">Blue Note</div>
                            <div class="aside-story-title">
                                {{ selectedTask?.taskName || latestTaskMoment?.title || '让任务记录保持轻微起伏' }}
                            </div>
                            <div class="aside-story-copy">
                                {{ selectedTask ? '详情已经和右侧记录区联动，你可以在这里继续切换任务，不会离开当前发布域。' : latestTaskMoment ? `${latestTaskMoment.status} · ${latestTaskMoment.time}` : '提交后的状态、阶段与快照会在这里留下更柔和的提醒。' }}
                            </div>
                            <div class="aside-story-pills">
                                <span class="hero-signal-chip">
                                    <span class="text-[var(--text-muted)]">总记录</span>
                                    <strong>{{ pageState.total }}</strong>
                                </span>
                                <span class="hero-signal-chip">
                                    <span class="text-[var(--text-muted)]">当前页</span>
                                    <strong>{{ pageState.pageNum }}/{{ pageState.pageSum }}</strong>
                                </span>
                            </div>
                        </section>

                        <section class="conversation-side-card xhs-ledger-card">
                            <div class="section-title-row">
                                <div>
                                    <div class="section-kicker">Task Ledger</div>
                                    <div class="mt-[8px] text-[20px] font-semibold text-[var(--text-primary)]">
                                        任务记录
                                    </div>
                                </div>
                                <span class="toolbar-chip xhs-toolbar-chip">
                                    第 {{ pageState.pageNum }} / {{ pageState.pageSum }} 页
                                </span>
                            </div>

                            <div v-if="recordLoading" class="empty-state mt-[18px] min-h-[220px]">
                                任务记录正从后端慢慢浮出来……
                            </div>
                            <div v-else-if="recordError" class="danger-inline mt-[18px]">
                                {{ recordError }}
                            </div>
                            <div v-else-if="pageState.list.length" class="mt-[18px] flex flex-col gap-[10px]">
                                <button
                                    v-for="task in pageState.list"
                                    :key="task.taskId"
                                    type="button"
                                    class="record-card"
                                    :class="selectedTaskId === task.taskId ? 'record-card--active' : ''"
                                    @click="openTaskDetail(task.taskId)"
                                >
                                    <div class="flex flex-wrap items-start justify-between gap-[12px]">
                                        <div class="min-w-0">
                                            <div class="truncate text-[15px] font-semibold text-[var(--text-primary)]">
                                                {{ task.taskName || task.taskId }}
                                            </div>
                                            <div class="mt-[6px] flex flex-wrap gap-[8px]">
                                                <span
                                                    class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                                    :class="resolveBadgeClass(task.taskStatus)"
                                                >
                                                    {{ renderTokenText(task.taskStatus) }}
                                                </span>
                                                <span
                                                    class="rounded-full border px-[10px] py-[6px] text-[11px] font-semibold"
                                                    :class="resolveBadgeClass(task.currentStage)"
                                                >
                                                    {{ renderTokenText(task.currentStage) }}
                                                </span>
                                            </div>
                                        </div>
                                        <div class="text-right text-[12px] text-[var(--text-muted)]">
                                            <div>{{ renderTokenText(task.publishMode) }}</div>
                                            <div class="mt-[4px]">{{ renderTokenText(task.publishType) }}</div>
                                        </div>
                                    </div>

                                    <div class="mt-[12px] flex flex-wrap items-center justify-between gap-[10px] text-[12px] text-[var(--text-secondary)]">
                                        <span>{{ formatDateTime(task.scheduledPublishAt) || '立即执行' }}</span>
                                        <span>{{ formatDateTime(task.updateTime) || '刚刚更新' }}</span>
                                    </div>
                                </button>

                                <div class="mt-[8px] flex items-center justify-between gap-[10px]">
                                    <button
                                        type="button"
                                        class="hero-ghost-button !min-h-[38px] !rounded-[14px]"
                                        :disabled="pageState.pageNum <= 1"
                                        @click="goPrevPage"
                                    >
                                        上一页
                                    </button>
                                    <div class="text-[12px] text-[var(--text-secondary)]">
                                        共 {{ pageState.total }} 条记录
                                    </div>
                                    <button
                                        type="button"
                                        class="hero-ghost-button !min-h-[38px] !rounded-[14px]"
                                        :disabled="pageState.pageNum >= pageState.pageSum"
                                        @click="goNextPage"
                                    >
                                        下一页
                                    </button>
                                </div>
                            </div>
                            <div v-else class="empty-state mt-[18px] min-h-[220px]">
                                还没有任务记录。提交第一条智能发布后，这里会变成一条有呼吸感的时间流。
                            </div>
                        </section>
                    </aside>
                </div>
            </div>
        </div>

        <Footer />
    </section>
</template>

<style scoped>
.xhs-publish-shell {
    background:
        radial-gradient(circle at 8% 10%, rgba(96, 165, 250, 0.18), transparent 24%),
        radial-gradient(circle at 86% 14%, rgba(125, 211, 252, 0.18), transparent 20%),
        radial-gradient(circle at 76% 82%, rgba(147, 197, 253, 0.12), transparent 24%),
        linear-gradient(180deg, rgba(246, 250, 255, 0.94), rgba(235, 244, 252, 0.92)),
        var(--bg-page);
}

.xhs-hero {
    border-color: rgba(148, 163, 184, 0.14);
    background:
        linear-gradient(135deg, rgba(255, 255, 255, 0.68), rgba(234, 244, 255, 0.54)),
        radial-gradient(circle at top right, rgba(96, 165, 250, 0.18), transparent 26%),
        radial-gradient(circle at 18% 20%, rgba(147, 197, 253, 0.12), transparent 22%);
    box-shadow:
        0 28px 70px rgba(107, 145, 182, 0.14),
        inset 0 1px 0 rgba(255, 255, 255, 0.6);
    backdrop-filter: blur(22px);
}

.hero-noise {
    position: absolute;
    inset: 0;
    background:
        linear-gradient(120deg, rgba(255, 255, 255, 0.24), transparent 28%, rgba(255, 255, 255, 0.08) 52%, transparent 76%),
        radial-gradient(circle at 82% 14%, rgba(96, 165, 250, 0.12), transparent 20%);
    pointer-events: none;
}

.xhs-serif {
    font-family: 'Georgia', 'Times New Roman', serif;
}

.hero-aura {
    position: absolute;
    border-radius: 999px;
    pointer-events: none;
    filter: blur(6px);
    opacity: 0.9;
}

.hero-aura--one {
    top: 44px;
    right: 140px;
    width: 180px;
    height: 180px;
    background: radial-gradient(circle, rgba(125, 211, 252, 0.34), rgba(125, 211, 252, 0));
    animation: auraDrift 13s ease-in-out infinite;
}

.hero-aura--two {
    bottom: -12px;
    left: 42%;
    width: 220px;
    height: 220px;
    background: radial-gradient(circle, rgba(96, 165, 250, 0.18), rgba(96, 165, 250, 0));
    animation: auraDrift 16s ease-in-out infinite reverse;
}

.hero-stage-grid {
    display: grid;
    gap: 18px;
    grid-template-columns: minmax(0, 1.1fr) minmax(320px, 430px);
    align-items: stretch;
}

.hero-copy-panel {
    display: flex;
    min-width: 0;
    flex-direction: column;
    justify-content: center;
}

.hero-signal-row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 18px;
}

.hero-signal-chip {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-height: 38px;
    border: 1px solid rgba(148, 163, 184, 0.16);
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.58);
    padding: 0 14px;
    backdrop-filter: blur(12px);
    box-shadow: 0 10px 22px rgba(15, 23, 42, 0.05);
}

.hero-signal-chip strong {
    color: var(--text-primary);
}

.hero-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
}

.hero-orbit-panel {
    display: grid;
    gap: 14px;
    align-content: start;
}

.hero-orbit-card {
    position: relative;
    overflow: hidden;
    border: 1px solid rgba(148, 163, 184, 0.14);
    border-radius: 24px;
    padding: 18px;
    backdrop-filter: blur(18px);
}

.hero-orbit-card::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0));
    pointer-events: none;
}

.hero-orbit-card--primary {
    background:
        radial-gradient(circle at top right, rgba(125, 211, 252, 0.18), transparent 34%),
        linear-gradient(180deg, rgba(255, 255, 255, 0.8), rgba(228, 241, 252, 0.74));
    box-shadow: 0 22px 44px rgba(79, 127, 167, 0.14);
}

.hero-orbit-card--secondary {
    background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.76), rgba(241, 248, 255, 0.72)),
        rgba(255, 255, 255, 0.7);
}

.hero-orbit-title {
    position: relative;
    margin-top: 14px;
    font-size: clamp(1.35rem, 2vw, 1.9rem);
    line-height: 1.18;
    font-weight: 700;
    color: var(--text-primary);
}

.hero-orbit-text {
    position: relative;
    margin-top: 10px;
    font-size: 13px;
    line-height: 1.9;
    color: var(--text-secondary);
}

.hero-mini-grid {
    position: relative;
    display: grid;
    gap: 10px;
    margin-top: 18px;
    grid-template-columns: repeat(3, minmax(0, 1fr));
}

.hero-note-card,
.pulse-chip-item {
    border: 1px solid rgba(148, 163, 184, 0.14);
    border-radius: 18px;
    background: rgba(255, 255, 255, 0.6);
    padding: 12px;
}

.hero-note-label,
.pulse-chip-label {
    font-size: 11px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--text-muted);
}

.hero-note-value,
.pulse-chip-value {
    margin-top: 8px;
    font-size: 13px;
    line-height: 1.7;
    font-weight: 600;
    color: var(--text-primary);
}

.hero-echo-line {
    display: flex;
    flex-direction: column;
    gap: 6px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.56);
    padding: 12px 14px;
}

.hero-echo-line span {
    font-size: 11px;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--text-muted);
}

.hero-echo-line strong {
    font-size: 13px;
    line-height: 1.7;
    color: var(--text-primary);
}

.hero-lower-grid {
    display: grid;
    gap: 18px;
    grid-template-columns: minmax(0, 1.08fr) minmax(310px, 0.76fr);
    align-items: start;
}

.xhs-stat-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    align-items: start;
}

.hero-ghost-button {
    min-height: 42px;
    border-radius: 16px;
    border: 1px solid rgba(148, 163, 184, 0.18);
    background: rgba(255, 255, 255, 0.7);
    padding: 0 16px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
    transition:
        transform 0.2s ease,
        background-color 0.2s ease,
        border-color 0.2s ease,
        box-shadow 0.2s ease;
}

.hero-ghost-button:hover:not(:disabled) {
    transform: translateY(-1px);
    border-color: rgba(96, 165, 250, 0.28);
    background: rgba(255, 255, 255, 0.92);
    box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.hero-ghost-button:disabled {
    cursor: not-allowed;
    opacity: 0.55;
}

.xhs-stat-card {
    background:
        linear-gradient(160deg, rgba(255, 255, 255, 0.84), rgba(236, 245, 254, 0.74)),
        rgba(255, 255, 255, 0.86);
    backdrop-filter: blur(14px);
    transition:
        transform 0.24s ease,
        border-color 0.24s ease,
        box-shadow 0.24s ease;
}

.xhs-stat-card:hover {
    transform: translateY(-3px);
    border-color: rgba(96, 165, 250, 0.24);
    box-shadow: 0 22px 42px rgba(79, 127, 167, 0.12);
}

.xhs-stat-card--1 {
    grid-column: span 2;
    min-height: 148px;
}

.xhs-stat-card--2 {
    min-height: 168px;
}

.xhs-stat-card--3 {
    min-height: 132px;
    transform: translateY(18px);
}

.xhs-stat-card--4 {
    grid-column: span 2;
    min-height: 144px;
}

.hero-snapshot-panel {
    min-height: 100%;
}

.hero-empty-note,
.upload-empty-note {
    border: 1px dashed rgba(148, 163, 184, 0.22);
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.34);
    padding: 18px;
    font-size: 13px;
    line-height: 1.8;
    color: var(--text-secondary);
}

.xhs-toolbar-chip {
    background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.74), rgba(239, 246, 255, 0.68)),
        rgba(255, 255, 255, 0.72);
}

.xhs-input,
.xhs-textarea {
    width: 100%;
    border-radius: 16px;
    border: 1px solid rgba(148, 163, 184, 0.18);
    background: rgba(255, 255, 255, 0.78);
    padding: 14px 16px;
    font-size: 14px;
    color: var(--text-primary);
    backdrop-filter: blur(10px);
    transition:
        border-color 0.2s ease,
        box-shadow 0.2s ease,
        background-color 0.2s ease;
}

.xhs-input:focus,
.xhs-textarea:focus {
    outline: none;
    border-color: rgba(96, 165, 250, 0.34);
    background: rgba(255, 255, 255, 0.96);
    box-shadow: 0 0 0 4px rgba(96, 165, 250, 0.08);
}

.xhs-textarea {
    min-height: 144px;
    resize: vertical;
    line-height: 1.75;
}

.xhs-workbench-panel {
    background:
        linear-gradient(160deg, rgba(255, 255, 255, 0.76), rgba(235, 244, 253, 0.68)),
        rgba(255, 255, 255, 0.72);
}

.workbench-heading {
    align-items: flex-start;
}

.workbench-heading__chips {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 10px;
}

.publish-stage-grid {
    display: grid;
    gap: 18px;
    grid-template-columns: minmax(0, 1.02fr) minmax(320px, 0.86fr);
    align-items: start;
}

.form-sheet {
    background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.84), rgba(240, 247, 255, 0.72)),
        rgba(255, 255, 255, 0.82);
}

.advanced-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: space-between;
    border-radius: 18px;
    border: 1px solid rgba(148, 163, 184, 0.18);
    background: rgba(255, 255, 255, 0.66);
    padding: 13px 14px;
    text-align: left;
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
    transition:
        transform 0.2s ease,
        border-color 0.2s ease,
        background-color 0.2s ease;
}

.advanced-toggle:hover {
    transform: translateY(-1px);
    border-color: rgba(96, 165, 250, 0.24);
    background: rgba(255, 255, 255, 0.92);
}

.advanced-sheet {
    display: grid;
    gap: 16px;
    border: 1px solid rgba(148, 163, 184, 0.12);
    border-radius: 22px;
    background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.7), rgba(237, 245, 252, 0.68)),
        rgba(255, 255, 255, 0.64);
    padding: 16px;
    backdrop-filter: blur(12px);
}

.publish-side-stack {
    display: grid;
    gap: 16px;
    align-content: start;
}

.upload-gallery-card,
.pulse-summary-card,
.quick-return-card {
    background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(236, 246, 255, 0.72)),
        rgba(255, 255, 255, 0.82);
}

.upload-dropzone {
    border: 1px dashed rgba(96, 165, 250, 0.24);
    border-radius: 24px;
    background:
        linear-gradient(145deg, rgba(255, 255, 255, 0.82), rgba(234, 244, 252, 0.72)),
        rgba(255, 255, 255, 0.76);
    padding: 18px;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.54);
}

.pulse-chip-grid {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(3, minmax(0, 1fr));
}

.xhs-primary-button {
    min-height: 44px;
    border-radius: 18px;
    border: 1px solid rgba(46, 101, 145, 0.24);
    background: linear-gradient(135deg, #296593 0%, #4d8bc3 42%, #85c4e8 100%);
    padding: 0 18px;
    font-size: 14px;
    font-weight: 700;
    color: white;
    box-shadow: 0 16px 34px rgba(79, 127, 167, 0.24);
    transition:
        transform 0.22s ease,
        filter 0.22s ease,
        box-shadow 0.22s ease;
}

.xhs-primary-button:hover:not(:disabled) {
    transform: translateY(-1px);
    filter: brightness(1.03);
    box-shadow: 0 18px 36px rgba(79, 127, 167, 0.32);
}

.xhs-primary-button:disabled {
    cursor: not-allowed;
    opacity: 0.58;
    box-shadow: none;
}

.xhs-upload-button,
.xhs-remove-button {
    border-radius: 14px;
    font-size: 13px;
    font-weight: 600;
    transition:
        transform 0.2s ease,
        background-color 0.2s ease,
        border-color 0.2s ease;
}

.xhs-upload-button {
    min-height: 42px;
    border: 1px solid rgba(96, 165, 250, 0.2);
    background: rgba(96, 165, 250, 0.1);
    padding: 0 16px;
    color: var(--accent-color);
}

.xhs-upload-button:hover {
    transform: translateY(-1px);
    border-color: rgba(96, 165, 250, 0.32);
    background: rgba(96, 165, 250, 0.14);
}

.xhs-remove-button {
    min-height: 34px;
    border: 1px solid rgba(244, 63, 94, 0.18);
    background: rgba(244, 63, 94, 0.08);
    padding: 0 12px;
    color: #be123c;
}

.xhs-remove-button:hover {
    transform: translateY(-1px);
    border-color: rgba(244, 63, 94, 0.3);
    background: rgba(244, 63, 94, 0.12);
}

.upload-card {
    border: 1px solid rgba(148, 163, 184, 0.14);
    border-radius: 22px;
    background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(236, 246, 255, 0.72)),
        rgba(255, 255, 255, 0.86);
    padding: 14px;
    backdrop-filter: blur(12px);
    transition:
        transform 0.22s ease,
        border-color 0.22s ease,
        box-shadow 0.22s ease;
}

.upload-card:hover {
    transform: translateY(-2px);
    border-color: rgba(96, 165, 250, 0.24);
    box-shadow: 0 18px 30px rgba(15, 23, 42, 0.08);
}

.snapshot-button,
.record-card,
.timeline-card {
    width: 100%;
    border-radius: 20px;
    border: 1px solid rgba(148, 163, 184, 0.14);
    background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(236, 246, 255, 0.72)),
        rgba(255, 255, 255, 0.86);
    padding: 14px 16px;
    text-align: left;
    backdrop-filter: blur(12px);
    transition:
        transform 0.22s ease,
        border-color 0.22s ease,
        box-shadow 0.22s ease,
        background-color 0.22s ease;
}

.snapshot-button:hover,
.record-card:hover,
.timeline-card:hover {
    transform: translateY(-2px);
    border-color: rgba(96, 165, 250, 0.24);
    box-shadow: 0 18px 30px rgba(15, 23, 42, 0.08);
}

.record-card--active {
    border-color: rgba(96, 165, 250, 0.28);
    background:
        radial-gradient(circle at top right, rgba(96, 165, 250, 0.18), transparent 34%),
        linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(228, 241, 252, 0.8)),
        rgba(255, 255, 255, 0.92);
}

.snapshot-button--airy {
    background:
        radial-gradient(circle at 100% 0%, rgba(125, 211, 252, 0.14), transparent 30%),
        linear-gradient(180deg, rgba(255, 255, 255, 0.84), rgba(236, 246, 255, 0.72)),
        rgba(255, 255, 255, 0.86);
}

.timeline-meta {
    display: flex;
    flex-direction: column;
    gap: 6px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.66);
    padding: 12px;
    font-size: 12px;
    color: var(--text-secondary);
}

.timeline-meta strong {
    font-size: 14px;
    color: var(--text-primary);
}

.xhs-code-block {
    margin-top: 14px;
    max-height: 240px;
    overflow: auto;
    border-radius: 16px;
    background: rgba(15, 23, 42, 0.92);
    padding: 14px;
    font-size: 12px;
    line-height: 1.7;
    color: #e2e8f0;
    white-space: pre-wrap;
    word-break: break-word;
}

.workspace-grid {
    grid-template-columns: minmax(0, 1fr) 390px;
}

.xhs-record-stage {
    position: sticky;
    top: 0;
}

.aside-story-card {
    position: relative;
    overflow: hidden;
    background:
        radial-gradient(circle at top right, rgba(125, 211, 252, 0.2), transparent 34%),
        linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(233, 244, 253, 0.72));
    backdrop-filter: blur(16px);
}

.aside-story-title {
    position: relative;
    margin-top: 10px;
    font-size: 20px;
    line-height: 1.35;
    font-weight: 700;
    color: var(--text-primary);
}

.aside-story-copy {
    position: relative;
    margin-top: 10px;
    font-size: 13px;
    line-height: 1.8;
    color: var(--text-secondary);
}

.aside-story-pills {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 16px;
}

.xhs-ledger-card {
    backdrop-filter: blur(16px);
}

.soft-fade-enter-active,
.soft-fade-leave-active {
    transition:
        opacity 0.2s ease,
        transform 0.2s ease;
}

.soft-fade-enter-from,
.soft-fade-leave-to {
    opacity: 0;
    transform: translateY(-6px);
}

@keyframes auraDrift {
    0%,
    100% {
        transform: translate3d(0, 0, 0) scale(1);
    }
    50% {
        transform: translate3d(0, -10px, 0) scale(1.04);
    }
}

[data-theme='dark'] .hero-ghost-button,
[data-theme='dark'] .xhs-input,
[data-theme='dark'] .xhs-textarea,
[data-theme='dark'] .timeline-meta,
[data-theme='dark'] .upload-card,
[data-theme='dark'] .snapshot-button,
[data-theme='dark'] .record-card,
[data-theme='dark'] .timeline-card,
[data-theme='dark'] .xhs-stat-card,
[data-theme='dark'] .hero-orbit-card,
[data-theme='dark'] .hero-note-card,
[data-theme='dark'] .pulse-chip-item,
[data-theme='dark'] .aside-story-card,
[data-theme='dark'] .upload-gallery-card,
[data-theme='dark'] .pulse-summary-card,
[data-theme='dark'] .quick-return-card {
    background-color: rgba(15, 23, 42, 0.72);
}

[data-theme='dark'] .xhs-upload-button {
    background: rgba(96, 165, 250, 0.16);
}

[data-theme='dark'] .xhs-remove-button {
    background: rgba(244, 63, 94, 0.14);
}

[data-theme='dark'] .record-card--active {
    background:
        radial-gradient(circle at top right, rgba(96, 165, 250, 0.16), transparent 32%),
        linear-gradient(180deg, rgba(30, 41, 59, 0.9), rgba(15, 23, 42, 0.88));
}

@media (max-width: 1220px) {
    .hero-stage-grid,
    .hero-lower-grid,
    .publish-stage-grid,
    .workspace-grid {
        grid-template-columns: minmax(0, 1fr);
    }

    .xhs-record-stage {
        position: static;
    }
}

@media (max-width: 960px) {
    .hero-mini-grid,
    .pulse-chip-grid,
    .xhs-stat-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .xhs-stat-card--1,
    .xhs-stat-card--4 {
        grid-column: span 1;
    }

    .xhs-stat-card--3 {
        transform: none;
    }
}

@media (max-width: 720px) {
    .hero-actions,
    .hero-signal-row,
    .workbench-heading__chips,
    .aside-story-pills {
        width: 100%;
    }

    .hero-actions .hero-ghost-button {
        flex: 1 1 160px;
    }
}

@media (max-width: 640px) {
    .hero-mini-grid,
    .pulse-chip-grid,
    .xhs-stat-grid {
        grid-template-columns: minmax(0, 1fr);
    }
}

@media (prefers-reduced-motion: reduce) {
    .hero-aura--one,
    .hero-aura--two {
        animation: none;
    }
}
</style>
