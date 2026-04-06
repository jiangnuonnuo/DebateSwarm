<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { marked } from 'marked';
import hljs from 'highlight.js';
import DOMPurify from 'dompurify';
import {
    dispatchArmory,
    executeAgentStream,
    insertSession,
    listSessions,
    listWorkAnswerMessages,
    listWorkSseMessages,
    queryAgentList,
    updateSession
} from '../request/api';
import { normalizeError, notifyAppError } from '../request/request';
import { formatMcpJson } from '../utils/StringUtil';
import { areCardListsEqual, areMessageListsEqual, createStableRecordId, toSafeTimestamp } from '../utils/MessageRenderUtil';
import { useAgentSettingsStore, useAgentStore, useChatStore, useSettingsStore, useWelcomeLaunchStore } from '../router/pinia';
import { getStrategyTone } from '../utils/StrategyTone';
import Footer from './Footer.vue';

const router = useRouter();
const agentStore = useAgentStore();
const chatStore = useChatStore();
const settingsStore = useAgentSettingsStore();
const uiSettingsStore = useSettingsStore();
const welcomeLaunchStore = useWelcomeLaunchStore();

const agentOptions = ref([]);
const pendingAgentId = ref('');
const agentDropdownOpen = ref(false);
const agentSelectRef = ref(null);

const leftScrollRef = ref(null);
const rightScrollRef = ref(null);
const inputValue = ref('');
const showSettings = ref(false);
const isLeftAtBottom = ref(true);
const isRightAtBottom = ref(true);
const messageLoading = ref(false);
const sendError = ref('');
const skipNextLoadSessionId = ref(null);
let autoRefreshTimer = null;

const settingsForm = reactive({
    maxRetry: settingsStore.maxRetry,
    maxRound: settingsStore.maxRound,
    maxPace: settingsStore.maxPace
});

const OVERVIEW_SECTION_TYPES = new Set(['summarizer_overview', 'replier_overview', 'evaluator_overview']);
const resolveAgentType = (value) => {
    const normalized = (value || '').toString().trim().toLowerCase();
    return normalized === 'step' || normalized === 'loop' || normalized === 'react' ? normalized : '';
};

const pickData = (resp, message = '操作失败') => {
    if (resp && typeof resp === 'object' && Object.prototype.hasOwnProperty.call(resp, 'code')) {
        if (resp.code !== 200) {
            const err = new Error(resp.info || message);
            err.status = 500;
            throw err;
        }
        return resp.data;
    }
    return resp?.data ?? resp?.result ?? resp;
};

const normalizeSessionType = (value) => (value ? value.toString().toLowerCase() : '');

const mapSession = (session) => {
    if (!session) return null;
    return {
        sessionId: session.sessionId,
        sessionUser: session.userName || session.sessionUser || '',
        title: session.sessionTitle || '新会话',
        sessionType: normalizeSessionType(session.sessionType || session.type),
        createdAt: session.createTime ? Date.parse(session.createTime) : Date.now(),
        messages: [],
        cards: []
    };
};

const SESSION_INVALID_HINT = '会话已失效，请新建会话';

const isInvalidSessionErrorMessage = (message) => {
    if (!message) return false;
    return (
        message.includes('会话不存在') ||
        message.includes('会话已失效') ||
        message.includes('会话类型不匹配') ||
        message.includes('无权限访问该会话') ||
        message.includes('无权限修改该会话')
    );
};

const listRemoteWorkSessions = async (options = {}) => {
    const resp = await listSessions(options);
    const list = pickData(resp, '获取会话失败') || [];
    const mapped = (Array.isArray(list) ? list : []).map(mapSession).filter(Boolean);
    return mapped.filter((item) => item.sessionType === 'work');
};

const refreshWorkSessions = async (options = {}) => {
    const workList = await listRemoteWorkSessions(options);
    agentStore.setSessions(workList);
    return workList;
};

const dropInvalidWorkSession = (sessionId = agentStore.currentSessionId) => {
    if (sessionId) {
        agentStore.removeSession(sessionId);
    }
    if (agentStore.sessions.length === 0 && chatStore.chats.length === 0) {
        router.replace('/welcome');
    }
    sendError.value = SESSION_INVALID_HINT;
};

const ensureWorkSessionValid = async (session, options = {}) => {
    if (!session?.sessionId) return null;
    const silentToast = Boolean(options.silentToast);
    try {
        const workList = await listRemoteWorkSessions(silentToast ? { toast: false } : {});
        const matched = workList.find((item) => item.sessionId === session.sessionId);
        if (!matched) {
            dropInvalidWorkSession(session.sessionId);
            return null;
        }
        return matched;
    } catch (error) {
        if (silentToast) {
            sendError.value = normalizeError(error).message || '获取会话失败';
        } else {
            notifyAppError(error, '获取会话失败');
        }
        return null;
    }
};

const mapMessage = (message, sessionId = '', seenMap = new Map()) => {
    const role = message?.messageRole || message?.role || 'assistant';
    const content = message?.messageContent || message?.content || '';
    const createTime = message?.createTime || message?.createdAt || '';
    return {
        id: createStableRecordId({
            sourceId: message?.messageSeq || message?.messageId || message?.id,
            sessionId,
            prefix: 'msg',
            signatureParts: [createTime, role, content],
            seenMap
        }),
        role,
        content,
        pending: false,
        error: null,
        createdAt: toSafeTimestamp(createTime)
    };
};

const mapCard = (message, sessionId = '', seenMap = new Map()) => {
    const raw = message?.messageContent || '';
    let parsed = null;
    try {
        parsed = raw ? JSON.parse(raw) : null;
    } catch (error) {
        parsed = null;
    }
    const payload = parsed && typeof parsed === 'object' ? parsed : { sectionContent: raw };
    const createTime = message?.createTime || '';
    return {
        id: createStableRecordId({
            sourceId: message?.messageSeq || payload?.id || message?.id,
            sessionId,
            prefix: 'card',
            signatureParts: [createTime, payload.sectionType, payload.sectionContent, payload.round, payload.pace, payload.step, payload.timestamp],
            seenMap
        }),
        clientType: payload.clientType || '',
        sectionType: payload.sectionType || '',
        sectionContent: payload.sectionContent || '',
        round: payload.round ?? null,
        pace: payload.pace ?? null,
        step: payload.step ?? null,
        timestamp: payload.timestamp ?? null
    };
};

const renderer = new marked.Renderer();
renderer.code = (code, infostring) => {
    const lang = (infostring || '').match(/\S*/)?.[0] || '';
    if (lang && hljs.getLanguage(lang)) {
        return `<pre><code class="hljs language-${lang}">${hljs.highlight(code, {
            language: lang,
            ignoreIllegals: true
        }).value}</code></pre>`;
    }
    return `<pre><code class="hljs">${hljs.highlightAuto(code).value}</code></pre>`;
};

marked.setOptions({ breaks: true, gfm: true, renderer });

const renderMarkdown = (text) => {
    if (!text) return '';
    return DOMPurify.sanitize(marked.parse(text), { ADD_ATTR: ['class'] });
};

const currentAgentId = computed({
    get: () => {
        const session = agentStore.currentSession;
        if (session) {
            return session.agentId || '';
        }
        return pendingAgentId.value || '';
    },
    set: (value) => {
        const session = agentStore.currentSession;
        if (session?.sessionId) {
            if (agentStore.isSessionSelectionLocked(session.sessionId)) {
                return;
            }
            agentStore.setSessionAgent(session.sessionId, value || '');
            return;
        }
        pendingAgentId.value = value || '';
    }
});
const isAgentLocked = computed(() => {
    const sessionId = agentStore.currentSession?.sessionId || '';
    if (!sessionId) return false;
    return agentStore.isSessionSelectionLocked(sessionId);
});

const currentAgentLabel = computed(() => {
    if (!currentAgentId.value) {
        return '选择 MiniAgent';
    }
    const match = agentOptions.value.find((item) => item.value === currentAgentId.value);
    return match?.label || currentAgentId.value;
});

const currentAgentType = computed(() => {
    if (!currentAgentId.value) return '';
    const match = agentOptions.value.find((item) => item.value === currentAgentId.value);
    return resolveAgentType(match?.agentType || '');
});

const isReactAgent = computed(() => currentAgentType.value === 'react');
const isDarkTheme = computed(() => uiSettingsStore.theme === 'dark');
const currentAgentTone = computed(() => getStrategyTone(currentAgentType.value || 'react', isDarkTheme.value));
const currentAgentTypeLabel = computed(() => (currentAgentType.value || '').toUpperCase());
const getStrategyBadgeStyle = (strategy) => {
    const tone = getStrategyTone(strategy, isDarkTheme.value);
    return {
        borderColor: tone.badgeBorder,
        backgroundColor: tone.badgeBg,
        color: tone.badgeText
    };
};
const currentAgentDesc = computed(() => {
    if (!currentAgentId.value) return '';
    const match = agentOptions.value.find((item) => item.value === currentAgentId.value);
    return (match?.desc || '').trim() || '暂无描述';
});

const fetchAgents = async () => {
    try {
        const resp = await queryAgentList();
        const list = pickData(resp, '获取 MiniAgent 列表失败') || [];
        const normalized = list
            .map((item) => {
                if (!item || typeof item !== 'object') return null;
                const agentId = item.agentId || '';
                const agentName = item.agentName || item.name || agentId;
                const agentDesc = item.agentDesc || item.desc || '';
                const agentType = resolveAgentType(item.agentType || item.type || '');
                if (!agentId) return null;
                return { label: agentName || agentId, value: agentId, desc: agentDesc, agentType };
            })
            .filter(Boolean);
        const seen = new Set();
        const unique = normalized.filter((item) => {
            if (seen.has(item.value)) return false;
            seen.add(item.value);
            return true;
        });
        agentOptions.value = unique;
        if (currentAgentId.value && !unique.some((item) => item.value === currentAgentId.value)) {
            currentAgentId.value = '';
        }
        return unique;
    } catch (error) {
        console.warn('获取 MiniAgent 列表失败', error);
        return [];
    }
};

const messages = computed(() => agentStore.currentMessages);
const cards = computed(() => agentStore.currentCards);
const sending = computed(() => agentStore.sending);
const userMessageCount = computed(() => messages.value.filter((item) => item.role === 'user').length);
const workLimitReached = computed(() => userMessageCount.value >= 3);
const currentSessionTitle = computed(() => agentStore.currentSession?.title || '智能体工作台');
const workSubtitle = computed(() => {
    if (sending.value) {
        return '执行过程与最终回答正在联动刷新，右侧仍可查看当前智能体与参数设置。';
    }
    if (messageLoading.value) {
        return '正在同步当前会话的执行卡片和回答内容。';
    }
    if (!cards.value.length && !messages.value.length) {
        return '先在右侧选择 MiniAgent，再发起一次任务，让过程卡片和结果面板一起工作。';
    }
    return `当前会话包含 ${cards.value.length} 张执行卡片，已累计 ${messages.value.length} 条回答记录。`;
});

const handleLeftScroll = () => {
    const el = leftScrollRef.value;
    if (!el) return;
    const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
    isLeftAtBottom.value = distance < 80;
};

const handleRightScroll = () => {
    const el = rightScrollRef.value;
    if (!el) return;
    const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
    isRightAtBottom.value = distance < 80;
};

const scrollLeftToBottom = (smooth = true) => {
    nextTick(() => {
        const el = leftScrollRef.value;
        if (!el) return;
        el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
    });
};

const scrollRightToBottom = (smooth = true) => {
    nextTick(() => {
        const el = rightScrollRef.value;
        if (!el) return;
        el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
    });
};

const loadWorkMessages = async (sessionId, options = {}) => {
    const silent = Boolean(options?.silent);
    if (!sessionId) {
        if (agentStore.currentSessionId) {
            agentStore.setSessionMessages(agentStore.currentSessionId, []);
            agentStore.setSessionCards(agentStore.currentSessionId, []);
        }
        return;
    }
    if (!silent) {
        messageLoading.value = true;
    }
    try {
        const [sseResp, answerResp] = await Promise.all([
            listWorkSseMessages({ sessionId }),
            listWorkAnswerMessages({ sessionId })
        ]);
        const sseList = pickData(sseResp, '获取会话卡片失败') || [];
        const answerList = pickData(answerResp, '获取会话消息失败') || [];
        const cardSeenMap = new Map();
        const messageSeenMap = new Map();
        const mappedCards = (Array.isArray(sseList) ? sseList : [])
            .map((item) => mapCard(item, sessionId, cardSeenMap))
            .filter((card) => !OVERVIEW_SECTION_TYPES.has(card.sectionType));
        const mappedMessages = (Array.isArray(answerList) ? answerList : []).map((item) => mapMessage(item, sessionId, messageSeenMap));
        if (sessionId) {
            const existingSession = agentStore.sessions.find((item) => item.sessionId === sessionId);
            const prevCards = existingSession?.cards || [];
            const prevMessages = existingSession?.messages || [];
            if (!areCardListsEqual(prevCards, mappedCards)) {
                agentStore.setSessionCards(sessionId, mappedCards);
            }
            if (!areMessageListsEqual(prevMessages, mappedMessages)) {
                agentStore.setSessionMessages(sessionId, mappedMessages);
            }
        }
    } catch (error) {
        const message = normalizeError(error).message || '获取消息失败';
        if (isInvalidSessionErrorMessage(message)) {
            dropInvalidWorkSession(agentStore.currentSessionId);
            return;
        }
        notifyAppError(error, '获取消息失败');
    } finally {
        if (!silent) {
            messageLoading.value = false;
        }
    }
};

const ensureWorkSession = async ({ forceNew = false, sessionTitle = '新会话', silentToast = false } = {}) => {
    if (!forceNew && agentStore.currentSession) {
        if (!agentStore.currentSessionId && agentStore.currentSession?.sessionId) {
            agentStore.setCurrentSessionId(agentStore.currentSession.sessionId);
        }
        return agentStore.currentSession;
    }
    try {
        const resp = await insertSession(
            { sessionTitle: sessionTitle || '新会话', sessionType: 'work' },
            silentToast ? { toast: false } : {}
        );
        const created = mapSession(pickData(resp, '创建会话失败'));
        if (created) {
            agentStore.upsertSession(created);
            if (forceNew) {
                skipNextLoadSessionId.value = created.sessionId;
            }
            agentStore.setCurrentSessionId(created.sessionId);
            if (pendingAgentId.value) {
                agentStore.setSessionAgent(created.sessionId, pendingAgentId.value);
            }
            return created;
        }
        const workList = await refreshWorkSessions(silentToast ? { toast: false } : {});
        const session = workList[0] || null;
        if (!session) return null;
        agentStore.setCurrentSessionId(session.sessionId);
        return session;
    } catch (error) {
        if (silentToast) {
            sendError.value = normalizeError(error).message || '创建会话失败';
        } else {
            notifyAppError(error, '创建会话失败');
        }
        return null;
    }
};

const renameSessionIfNeeded = async (session, content, options = {}) => {
    if (!session) return;
    if (session.title && session.title !== '新会话') return;
    const silentToast = Boolean(options.silentToast);
    const nextTitle = content.slice(0, 20) || '新会话';
    agentStore.updateSessionTitle(session.sessionId, nextTitle);
    try {
        await updateSession({ sessionId: session.sessionId, sessionTitle: nextTitle }, silentToast ? { toast: false } : {});
    } catch (error) {
        if (!silentToast) {
            notifyAppError(error, '更新会话失败');
        }
    }
};

watch(
    cards,
    () => {
        if (isLeftAtBottom.value) scrollLeftToBottom(true);
    },
    { deep: true }
);

watch(
    messages,
    () => {
        if (isRightAtBottom.value) scrollRightToBottom(true);
    },
    { deep: true }
);

watch(
    () => inputValue.value,
    () => {
        if (sendError.value && userMessageCount.value < 3) {
            sendError.value = '';
        }
    }
);

watch(
    userMessageCount,
    (count) => {
        if (count >= 3) {
            sendError.value = '当前会话已达到 3 条用户消息上限，请新建会话';
        }
    },
    { immediate: true }
);

watch(isAgentLocked, (locked) => {
    if (locked) {
        agentDropdownOpen.value = false;
    }
});

watch(
    () => agentStore.currentSessionId,
    async (sessionId) => {
        sendError.value = '';
        if (sessionId && skipNextLoadSessionId.value === sessionId) {
            skipNextLoadSessionId.value = null;
            nextTick(() => {
                scrollLeftToBottom(false);
                scrollRightToBottom(false);
            });
            return;
        }
        const session = agentStore.currentSession;
        if (session?.sessionId) {
            await loadWorkMessages(session.sessionId);
        }
        nextTick(() => {
            scrollLeftToBottom(false);
            scrollRightToBottom(false);
        });
    },
    { immediate: true }
);

const toggleAgentDropdown = () => {
    if (agentOptions.value.length === 0 || isAgentLocked.value) return;
    agentDropdownOpen.value = !agentDropdownOpen.value;
};

const selectAgent = async (value) => {
    if (isAgentLocked.value) return;
    currentAgentId.value = value;
    agentDropdownOpen.value = false;
    try {
        await dispatchArmory({ armoryType: 'work', armoryId: value });
    } catch (error) {
        console.warn('绑定 Work armory 失败', error);
    }
};

const handleClickOutside = (event) => {
    const target = event.target;
    const inAgent = agentSelectRef.value && agentSelectRef.value.contains(target);
    if (!inAgent) agentDropdownOpen.value = false;
};

const handleEscClose = (event) => {
    if (event.key === 'Escape') {
        agentDropdownOpen.value = false;
    }
};

const handleKeydown = (event) => {
    if (event.key === 'Enter' && event.metaKey) {
        event.preventDefault();
        sendMessage();
        return;
    }
    if (event.key === 'Escape') {
        agentDropdownOpen.value = false;
    }
};

const openSettings = () => {
    settingsForm.maxRetry = settingsStore.maxRetry;
    settingsForm.maxRound = settingsStore.maxRound;
    settingsForm.maxPace = settingsStore.maxPace;
    showSettings.value = true;
};

const clampNumber = (value, min, max, fallback = min) => {
    const num = Number(value);
    if (!Number.isFinite(num)) return fallback;
    return Math.min(max, Math.max(min, Math.floor(num)));
};

const saveSettings = () => {
    settingsStore.updateSettings({
        maxRetry: clampNumber(settingsForm.maxRetry, 1, 3, 2),
        maxRound: clampNumber(settingsForm.maxRound, 1, 3, 2),
        maxPace: clampNumber(settingsForm.maxPace, 3, 5, 3)
    });
    showSettings.value = false;
};

const rangeStyle = (value, min = 1, max = 3) => {
    const clamped = clampNumber(value, min, max, min);
    const denominator = Math.max(1, max - min);
    const percent = ((clamped - min) / denominator) * 100;
    return {
        background: `linear-gradient(90deg, var(--accent-color) ${percent}%, var(--progress-track) ${percent}%)`
    };
};

const resolveAgentDesc = (agentId) => {
    const match = agentOptions.value.find((item) => item.value === agentId);
    return (match?.desc || '').trim() || '暂无';
};

const buildExecutePayload = (userMessage, sessionId, agentId) => ({
    agentId: agentId || currentAgentId.value,
    agentDesc: resolveAgentDesc(agentId || currentAgentId.value),
    userMessage,
    sessionId,
    maxRound: settingsStore.maxRound,
    maxRetry: settingsStore.maxRetry,
    maxPace: clampNumber(settingsStore.maxPace, 3, 5, 3)
});

const sendMessage = async (options = {}) => {
    const content = (options.content ?? inputValue.value).trim();
    const requestedAgentId = (options.agentId || '').trim();
    const resolvedAgentId = requestedAgentId || currentAgentId.value;
    if (!content || sending.value || !resolvedAgentId) return;
    sendError.value = '';
    const session = await ensureWorkSession({
        forceNew: Boolean(options.forceNew),
        sessionTitle: options.sessionTitle || '新会话',
        silentToast: true
    });
    if (!session) return;
    const validSession = await ensureWorkSessionValid(session, { silentToast: true });
    if (!validSession) return;
    if (userMessageCount.value >= 3) {
        sendError.value = '当前会话已达到 3 条用户消息上限，请新建会话';
        return;
    }
    if (validSession?.sessionId) {
        agentStore.setSessionAgent(validSession.sessionId, resolvedAgentId);
    }
    agentStore.stopCurrentRequest();
    agentStore.setSending(true);
    const controller = new AbortController();
    agentStore.setAbortController(controller);
    agentStore.setSessionSelectionLocked(validSession.sessionId, true);
    agentDropdownOpen.value = false;
    agentStore.addUserMessage(content);
    renameSessionIfNeeded(validSession, content, { silentToast: true });
    if (!Object.prototype.hasOwnProperty.call(options, 'content')) {
        inputValue.value = '';
    }
    scrollRightToBottom(true);
    await runExecute(content, controller, validSession.sessionId, resolvedAgentId);
};

const runExecute = async (content, controller, sessionId, agentId) => {
    const assistantMessage = agentStore.addAssistantMessage({ pending: true, content: '' });
    const events = [];
    let closed = false;

    const finish = () => {
        if (closed) return;
        closed = true;
        const reversed = [...events].reverse();
        const summarizerEvent = reversed.find((item) => item?.sectionType === 'summarizer_overview');
        const replierEvent = reversed.find((item) => item?.sectionType === 'replier_overview');
        const evaluatorEvent = reversed.find((item) => item?.sectionType === 'evaluator_overview');
        const answer = summarizerEvent?.sectionContent || replierEvent?.sectionContent || evaluatorEvent?.sectionContent || '';
        agentStore.updateAssistantMessage(assistantMessage.id, {
            content: answer || '（无内容）',
            pending: false,
            error: null
        });
        agentStore.setSending(false);
        agentStore.setAbortController(null);
        scrollRightToBottom(true);
    };

    const handleError = (error) => {
        if (closed) return;
        const friendly = normalizeError(error);
        closed = true;
        agentStore.updateAssistantMessage(assistantMessage.id, {
            content: friendly.message || '请求失败',
            pending: false,
            error: friendly
        });
        agentStore.setSending(false);
        agentStore.setAbortController(null);
        scrollRightToBottom(true);
    };

    try {
        await executeAgentStream({
            ...buildExecutePayload(content, sessionId, agentId),
            signal: controller.signal,
            onData: (payload) => {
                if (typeof payload === 'string') {
                    handleError(new Error(payload));
                    return;
                }
                if (!payload || typeof payload !== 'object') return;
                const event = {
                    clientType: payload.clientType || '',
                    sectionType: payload.sectionType || '',
                    sectionContent: payload.sectionContent || '',
                    round: payload.round ?? null,
                    pace: payload.pace ?? null,
                    step: payload.step ?? null,
                    timestamp: payload.timestamp ?? null
                };
                events.push(event);
                if (!OVERVIEW_SECTION_TYPES.has(event.sectionType)) {
                    agentStore.addCard(event);
                }
                if (isLeftAtBottom.value) scrollLeftToBottom(true);
            },
            onError: handleError,
            onDone: finish
        });
    } catch (error) {
        handleError(error);
    }
};

const handleStop = () => {
    if (!sending.value) return;
    agentStore.stopCurrentRequest();
    const lastAssistant = [...messages.value].filter((item) => item.role === 'assistant').pop();
    if (lastAssistant && lastAssistant.pending) {
        agentStore.updateAssistantMessage(lastAssistant.id, {
            pending: false,
            error: { message: '已停止生成' }
        });
    }
};

const getContent = (message) => {
    if (!message?.content) return '';
    const raw = message.content.toString();
    return formatMcpJson(raw);
};

const getCardContent = (card) => {
    if (!card?.sectionContent) return '';
    return formatMcpJson(card.sectionContent);
};

const consumeWelcomeLaunchTask = async () => {
    const task = welcomeLaunchStore.takeTask('work');
    const prompt = (task?.prompt || '').trim();
    if (!prompt) return;

    if (!agentOptions.value.length) {
        await fetchAgents();
    }

    if (task.agentId) {
        pendingAgentId.value = task.agentId;
        if (!isAgentLocked.value) {
            currentAgentId.value = task.agentId;
            try {
                await dispatchArmory({ armoryType: 'work', armoryId: task.agentId });
            } catch (error) {
                console.warn('绑定 Work armory 失败', error);
            }
        }
    } else if (!currentAgentId.value && agentOptions.value.length > 0) {
        currentAgentId.value = agentOptions.value[0].value;
    }

    if (!currentAgentId.value) {
        sendError.value = '暂无可用 MiniAgent，请先在后台配置 MiniAgent';
        return;
    }

    const session = await ensureWorkSession({
        forceNew: true,
        sessionTitle: task.sessionTitle || '新会话',
        silentToast: true
    });
    if (!session) return;
    const validSession = await ensureWorkSessionValid(session, { silentToast: true });
    if (!validSession) return;

    const resolvedAgentId = (task.agentId || currentAgentId.value || '').trim();
    if (!resolvedAgentId) {
        sendError.value = '暂无可用 MiniAgent，请先在后台配置 MiniAgent';
        return;
    }
    agentStore.setSessionAgent(validSession.sessionId, resolvedAgentId);
    currentAgentId.value = resolvedAgentId;

    inputValue.value = prompt;
    await nextTick();
    await sendMessage({ agentId: resolvedAgentId });
};

onMounted(() => {
    document.addEventListener('click', handleClickOutside);
    window.addEventListener('keydown', handleEscClose);
    scrollLeftToBottom(false);
    scrollRightToBottom(false);
    fetchAgents().then(async () => {
        if (agentStore.currentSession) {
            await ensureWorkSessionValid(agentStore.currentSession);
        }
        await consumeWelcomeLaunchTask();
    });
    autoRefreshTimer = window.setInterval(async () => {
        if (sending.value || messageLoading.value) return;
        const session = agentStore.currentSession;
        const sessionId = session?.sessionId || '';
        if (!sessionId) return;
        await loadWorkMessages(sessionId, { silent: true });
    }, 5000);
});

onBeforeUnmount(() => {
    document.removeEventListener('click', handleClickOutside);
    window.removeEventListener('keydown', handleEscClose);
    if (autoRefreshTimer) {
        window.clearInterval(autoRefreshTimer);
        autoRefreshTimer = null;
    }
});
</script>

<template>
    <section class="flex h-screen flex-col bg-[var(--bg-page)] overflow-hidden">
        <!-- 顶部紧凑 Header -->
        <header class="sticky top-0 z-30 flex h-[64px] shrink-0 items-center border-b border-[var(--border-color)] bg-[var(--panel-bg)] px-6 backdrop-blur-xl">
            <div class="mx-auto flex w-full max-w-[var(--workspace-max)] items-center justify-between gap-4">
                <div class="flex items-center gap-6 overflow-hidden">
                    <div class="flex items-center gap-3 shrink-0">
                        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-[var(--accent-color)] text-white shadow-lg shadow-blue-500/20">
                            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                                <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" stroke-linecap="round" />
                            </svg>
                        </div>
                        <h1 class="truncate text-lg font-bold tracking-tight text-[var(--text-primary)]">
                            {{ currentSessionTitle }}
                        </h1>
                    </div>

                    <!-- 顶部选择器组 -->
                    <div class="hidden h-8 w-[1px] bg-[var(--border-color)] md:block"></div>
                    
                    <div class="flex items-center gap-2">
                        <!-- MiniAgent 选择器 -->
                        <div class="relative">
                            <button
                                ref="agentSelectRef"
                                type="button"
                                class="flex h-9 items-center gap-2 rounded-full border border-[var(--border-color)] bg-[var(--bg-page)] px-4 text-sm font-medium text-[var(--text-primary)] transition-all hover:border-[var(--accent-color)] hover:bg-blue-50/50"
                                :class="{ 'opacity-70 cursor-not-allowed': isAgentLocked }"
                                @click="toggleAgentDropdown"
                            >
                                <span class="text-[var(--text-muted)]">MiniAgent:</span>
                                <span class="max-w-[120px] truncate">{{ currentAgentLabel }}</span>
                                <svg class="h-4 w-4 text-[var(--text-muted)] transition-transform" :class="{ 'rotate-180': agentDropdownOpen }" viewBox="0 0 20 20" fill="currentColor">
                                    <path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd" />
                                </svg>
                            </button>
                            
                            <div v-if="agentDropdownOpen" class="absolute left-0 top-full z-50 mt-2 w-64 rounded-2xl border border-[var(--border-color)] bg-white p-2 shadow-2xl ring-1 ring-black/5">
                                <div v-for="item in agentOptions" :key="item.value" 
                                    class="flex cursor-pointer items-center justify-between rounded-xl px-3 py-2 text-sm transition-colors hover:bg-blue-50"
                                    :class="item.value === currentAgentId ? 'bg-blue-50 text-[var(--accent-color)] font-semibold' : 'text-[var(--text-primary)]'"
                                    @click.stop="selectAgent(item.value)"
                                >
                                    <span class="truncate">{{ item.label }}</span>
                                    <svg v-if="item.value === currentAgentId" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                                        <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
                                    </svg>
                                </div>
                            </div>
                        </div>

                        <!-- 策略展示 -->
                        <div v-if="currentAgentTypeLabel" class="flex h-9 items-center gap-2 rounded-full border border-[var(--border-color)] bg-[var(--bg-page)] px-4 text-sm font-medium">
                            <span class="text-[var(--text-muted)]">Strategy:</span>
                            <span class="text-[var(--accent-color)]">{{ currentAgentTypeLabel }}</span>
                        </div>
                    </div>
                </div>

                <div class="flex items-center gap-3">
                    <button
                        class="flex h-9 items-center gap-2 rounded-full border border-[var(--accent-color)] bg-white px-4 text-sm font-semibold text-[var(--accent-color)] transition-all hover:bg-blue-50"
                        type="button"
                        @click="openSettings"
                    >
                        参数设置
                    </button>
                </div>
            </div>
        </header>

        <!-- 主体内容区域：移除侧边栏，改为通透双栏布局 -->
        <main class="flex-1 overflow-hidden">
            <div class="mx-auto grid h-full max-w-[var(--workspace-max)] grid-cols-2 gap-0 divide-x divide-[var(--border-color)]">
                <!-- 左侧：执行过程 (Process Lane) -->
                <div class="flex flex-col min-h-0">
                    <div class="flex items-center justify-between border-b border-[var(--border-color)] bg-[var(--bg-page)] px-6 py-3">
                        <div class="flex items-center gap-2">
                            <div class="h-2 w-2 rounded-full bg-blue-500 animate-pulse"></div>
                            <span class="text-sm font-bold tracking-wider text-[var(--text-muted)] uppercase">Process Lane</span>
                        </div>
                        <div class="text-[11px] font-medium text-[var(--text-muted)]">
                            Cards: {{ cards.length }}
                        </div>
                    </div>
                    
                    <div
                        ref="leftScrollRef"
                        class="flex-1 overflow-y-auto px-6 py-6 scroll-smooth"
                        @scroll="handleLeftScroll"
                    >
                        <div class="flex flex-col gap-4">
                            <div v-if="messageLoading" class="notice-inline">同步执行状态...</div>
                            <div
                                v-for="card in cards"
                                :key="card.id"
                                class="group relative overflow-hidden rounded-2xl border border-[var(--border-color)] bg-white p-4 transition-all hover:border-blue-200 hover:shadow-md"
                            >
                                <div class="mb-3 flex flex-wrap items-center gap-2">
                                    <span class="rounded-md bg-blue-50 px-2 py-0.5 text-[10px] font-bold text-blue-600 uppercase">{{ card.clientType || '-' }}</span>
                                    <span class="rounded-md bg-slate-50 px-2 py-0.5 text-[10px] font-bold text-slate-500 uppercase">{{ card.sectionType || '-' }}</span>
                                    <div class="ml-auto flex items-center gap-2 text-[10px] font-medium text-slate-400">
                                        <span v-if="card.round !== null">Round {{ card.round }}</span>
                                        <span v-if="card.pace !== null">Pace {{ card.pace }}</span>
                                        <span v-if="card.step !== null">Step {{ card.step }}</span>
                                    </div>
                                </div>
                                <div class="text-sm leading-relaxed text-[var(--text-primary)]">
                                    {{ getCardContent(card) }}
                                </div>
                            </div>
                            <div v-if="cards.length === 0" class="flex flex-col items-center justify-center py-20 text-[var(--text-muted)]">
                                <svg class="mb-3 h-12 w-12 opacity-20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                                    <path d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" stroke-linecap="round" stroke-linejoin="round" />
                                </svg>
                                <p class="text-sm">等待任务执行过程...</p>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 右侧：最终回答 (Answer Lane) -->
                <div class="flex flex-col min-h-0 bg-white/40 backdrop-blur-sm">
                    <div class="flex items-center justify-between border-b border-[var(--border-color)] bg-[var(--bg-page)]/50 px-6 py-3">
                        <div class="flex items-center gap-2">
                            <div class="h-2 w-2 rounded-full bg-blue-600"></div>
                            <span class="text-sm font-bold tracking-wider text-[var(--text-muted)] uppercase">Answer Lane</span>
                        </div>
                        <div class="text-[11px] font-medium text-[var(--text-muted)]">
                            Messages: {{ messages.length }}
                        </div>
                    </div>

                    <div
                        ref="rightScrollRef"
                        class="flex-1 overflow-y-auto px-6 py-6 scroll-smooth"
                        @scroll="handleRightScroll"
                    >
                        <div class="mx-auto flex w-full max-w-3xl flex-col gap-6">
                            <div
                                v-for="message in messages"
                                :key="message.id"
                                class="flex w-full"
                                :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
                            >
                                <div class="flex max-w-[85%] flex-col gap-2" :class="message.role === 'user' ? 'items-end' : 'items-start'">
                                    <div class="flex items-center gap-2 px-1 text-[11px] font-bold text-[var(--text-muted)] uppercase">
                                        <span>{{ message.role === 'user' ? 'Input' : 'Result' }}</span>
                                        <span v-if="message.pending" class="flex items-center gap-1 text-blue-500">
                                            <span class="h-1 w-1 animate-ping rounded-full bg-blue-500"></span>
                                            Running
                                        </span>
                                    </div>
                                    <div
                                        class="relative rounded-2xl border p-4 transition-all"
                                        :class="[
                                            message.error
                                                ? 'bg-red-50 border-red-100 text-red-600'
                                                : message.role === 'user'
                                                    ? 'bg-[var(--accent-color)] border-[var(--accent-color)] text-white shadow-lg shadow-blue-500/10'
                                                    : 'bg-white border-[var(--border-color)] shadow-sm hover:shadow-md',
                                            message.pending ? 'border-dashed' : 'border-solid'
                                        ]"
                                    >
                                        <!-- 消息内容 -->
                                        <div v-if="message.pending && message.role === 'assistant' && !message.content" class="flex items-center gap-2 py-1">
                                            <div class="flex gap-1">
                                                <div class="h-1.5 w-1.5 animate-bounce rounded-full bg-blue-400"></div>
                                                <div class="h-1.5 w-1.5 animate-bounce rounded-full bg-blue-400 [animation-delay:0.2s]"></div>
                                                <div class="h-1.5 w-1.5 animate-bounce rounded-full bg-blue-400 [animation-delay:0.4s]"></div>
                                            </div>
                                            <span class="text-xs text-slate-400 font-medium">Thinking...</span>
                                        </div>
                                        <div v-else-if="message.role === 'user' || message.pending" class="whitespace-pre-wrap break-all text-sm leading-relaxed">
                                            {{ getContent(message) }}
                                        </div>
                                        <div v-else
                                            class="markdown-body text-sm leading-relaxed [&_pre]:bg-slate-900 [&_pre]:p-4 [&_pre]:rounded-xl [&_code]:bg-slate-100 [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:rounded-md"
                                            v-html="renderMarkdown(getContent(message))"
                                        ></div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </main>

        <!-- 底部输入区域 -->
        <div class="shrink-0 border-t border-[var(--border-color)] bg-[var(--bg-page)]/80 p-4 backdrop-blur-md">
            <div class="mx-auto max-w-4xl">
                <div class="relative overflow-hidden rounded-2xl border border-[var(--border-color)] bg-white shadow-2xl transition-all focus-within:border-[var(--accent-color)] focus-within:ring-2 focus-within:ring-blue-500/10">
                    <textarea
                        v-model="inputValue"
                        class="w-full resize-none bg-transparent px-4 py-3 text-sm focus:outline-none disabled:opacity-50"
                        rows="3"
                        placeholder="描述你的任务需求..."
                        :disabled="sending"
                        @keydown="handleKeydown"
                    ></textarea>
                    
                    <div class="flex items-center justify-between border-t border-slate-50 bg-slate-50/30 px-3 py-2">
                        <div class="flex items-center gap-4">
                            <div v-if="sendError" class="text-[11px] font-bold text-[var(--accent-color)]">{{ sendError }}</div>
                            <div v-else class="text-[11px] font-medium text-slate-400">⌘ + Enter 发送</div>
                        </div>
                        
                        <div class="flex items-center gap-2">
                            <button
                                v-if="sending"
                                class="h-8 rounded-lg border border-slate-200 bg-white px-3 text-xs font-bold text-slate-600 transition-all hover:bg-slate-50"
                                @click="handleStop"
                            >
                                停止
                            </button>
                            <button
                                class="h-8 rounded-lg bg-[var(--accent-color)] px-4 text-xs font-bold text-white shadow-lg shadow-blue-500/20 transition-all hover:brightness-110 disabled:grayscale disabled:opacity-50"
                                :disabled="sending || !inputValue.trim() || !currentAgentId || workLimitReached"
                                @click="sendMessage"
                            >
                                {{ sending ? '执行中...' : '发送任务' }}
                            </button>
                        </div>
                    </div>
                </div>
                <div class="mt-2 text-center text-[10px] text-slate-400">
                    任务流转过程中会自动同步执行步骤与最终回复
                </div>
            </div>
        </div>

        <Footer />

        <div v-if="showSettings" class="fixed inset-0 z-[20] grid place-items-center bg-[rgba(0,0,0,0.35)] p-[20px]" @click.self="showSettings = false">
            <div class="w-full max-w-[520px] rounded-[16px] border border-[var(--border-color)] bg-white shadow-[0_20px_50px_rgba(15,23,42,0.2)]">
                <div class="flex items-center justify-between border-b border-[var(--border-color)] px-[18px] pt-[14px] pb-[10px]">
                    <div class="text-[18px] font-bold">回答设置</div>
                    <button class="text-[22px] text-[var(--text-secondary)]" type="button" @click="showSettings = false">×</button>
                </div>
                <div class="flex flex-col gap-[14px] px-[18px] py-[14px]">
                    <div class="flex flex-col gap-[8px]">
                        <div class="inline-flex items-center gap-[8px]">
                            <label class="font-semibold text-[var(--text-primary)]">maxRetry / 最多重试</label>
                            <span
                                class="inline-flex h-[24px] items-center rounded-full border px-[10px] text-[11px] font-bold uppercase tracking-[0.08em]"
                                :style="getStrategyBadgeStyle('step')"
                            >
                                STEP
                            </span>
                        </div>
                        <div class="flex flex-col gap-[10px]">
                            <input
                                v-model.number="settingsForm.maxRetry"
                                type="range"
                                min="1"
                                max="3"
                                step="1"
                                class="h-[6px] w-full cursor-pointer appearance-none rounded-full bg-[var(--progress-track)] [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-[14px] [&::-webkit-slider-thumb]:w-[14px] [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-[var(--accent-color)] [&::-webkit-slider-thumb]:shadow-[0_6px_14px_rgba(47,124,246,0.35)] [&::-moz-range-thumb]:h-[14px] [&::-moz-range-thumb]:w-[14px] [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-[var(--accent-color)]"
                                :style="rangeStyle(settingsForm.maxRetry, 1, 3)"
                            />
                            <div class="flex items-center justify-between px-[2px]">
                                <div v-for="value in [1, 2, 3]" :key="`retry-dot-${value}`" class="flex flex-col items-center gap-[6px]">
                                    <span
                                        class="h-[8px] w-[8px] rounded-full"
                                        :class="value === settingsForm.maxRetry ? 'bg-[var(--accent-color)]' : 'bg-[#cbd5e1]'"
                                    ></span>
                                    <span
                                        class="text-[12px]"
                                        :class="settingsForm.maxRetry === value ? 'text-[var(--accent-color)] font-semibold' : 'text-[var(--text-secondary)]'"
                                    >
                                        {{ value }}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="flex flex-col gap-[8px]">
                        <div class="inline-flex items-center gap-[8px]">
                            <label class="font-semibold text-[var(--text-primary)]">maxRound / 最大轮次</label>
                            <span
                                class="inline-flex h-[24px] items-center rounded-full border px-[10px] text-[11px] font-bold uppercase tracking-[0.08em]"
                                :style="getStrategyBadgeStyle('loop')"
                            >
                                LOOP
                            </span>
                        </div>
                        <div class="flex flex-col gap-[10px]">
                            <input
                                v-model.number="settingsForm.maxRound"
                                type="range"
                                min="1"
                                max="3"
                                step="1"
                                class="h-[6px] w-full cursor-pointer appearance-none rounded-full bg-[var(--progress-track)] [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-[14px] [&::-webkit-slider-thumb]:w-[14px] [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-[var(--accent-color)] [&::-webkit-slider-thumb]:shadow-[0_6px_14px_rgba(47,124,246,0.35)] [&::-moz-range-thumb]:h-[14px] [&::-moz-range-thumb]:w-[14px] [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-[var(--accent-color)]"
                                :style="rangeStyle(settingsForm.maxRound, 1, 3)"
                            />
                            <div class="flex items-center justify-between px-[2px]">
                                <div v-for="value in [1, 2, 3]" :key="`round-dot-${value}`" class="flex flex-col items-center gap-[6px]">
                                    <span
                                        class="h-[8px] w-[8px] rounded-full"
                                        :class="value === settingsForm.maxRound ? 'bg-[var(--accent-color)]' : 'bg-[#cbd5e1]'"
                                    ></span>
                                    <span
                                        class="text-[12px]"
                                        :class="settingsForm.maxRound === value ? 'text-[var(--accent-color)] font-semibold' : 'text-[var(--text-secondary)]'"
                                    >
                                        {{ value }}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="flex flex-col gap-[8px]">
                        <div class="inline-flex items-center gap-[8px]">
                            <label class="font-semibold text-[var(--text-primary)]">maxPace / 最大步伐</label>
                            <span
                                class="inline-flex h-[24px] items-center rounded-full border px-[10px] text-[11px] font-bold uppercase tracking-[0.08em]"
                                :style="getStrategyBadgeStyle('react')"
                            >
                                REACT
                            </span>
                        </div>
                        <div class="flex flex-col gap-[10px]">
                            <input
                                v-model.number="settingsForm.maxPace"
                                type="range"
                                min="3"
                                max="5"
                                step="1"
                                class="h-[6px] w-full cursor-pointer appearance-none rounded-full bg-[var(--progress-track)] [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-[14px] [&::-webkit-slider-thumb]:w-[14px] [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-[var(--accent-color)] [&::-webkit-slider-thumb]:shadow-[0_6px_14px_rgba(47,124,246,0.35)] [&::-moz-range-thumb]:h-[14px] [&::-moz-range-thumb]:w-[14px] [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-[var(--accent-color)]"
                                :style="rangeStyle(settingsForm.maxPace, 3, 5)"
                            />
                            <div class="flex items-center justify-between px-[2px]">
                                <div v-for="value in [3, 4, 5]" :key="`pace-dot-${value}`" class="flex flex-col items-center gap-[6px]">
                                    <span
                                        class="h-[8px] w-[8px] rounded-full"
                                        :class="value === settingsForm.maxPace ? 'bg-[var(--accent-color)]' : 'bg-[#cbd5e1]'"
                                    ></span>
                                    <span
                                        class="text-[12px]"
                                        :class="settingsForm.maxPace === value ? 'text-[var(--accent-color)] font-semibold' : 'text-[var(--text-secondary)]'"
                                    >
                                        {{ value }}
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="flex justify-end gap-[10px] border-t border-[var(--border-color)] px-[18px] pt-[12px] pb-[16px]">
                    <button
                        class="inline-flex items-center justify-center rounded-[12px] border border-[var(--border-color)] bg-white px-[14px] py-[9px] font-bold leading-[1.1] text-[var(--text-primary)] transition-all duration-200 hover:bg-[#f7f9fc]"
                        type="button"
                        @click="showSettings = false"
                    >
                        取消
                    </button>
                    <button
                        class="inline-flex items-center justify-center rounded-[12px] border border-[var(--accent-color)] bg-[var(--accent-color)] px-[14px] py-[9px] font-bold leading-[1.1] text-white transition-all duration-200 hover:brightness-95"
                        type="button"
                        @click="saveSettings"
                    >
                        保存
                    </button>
                </div>
            </div>
        </div>
    </section>
</template>

<style scoped>
.caret {
    display: inline-block;
    width: 0;
    height: 0;
    border-left: 6px solid transparent;
    border-right: 6px solid transparent;
    border-top: 7px solid #94a3b8;
    transform-origin: center;
    transition: transform 0.15s ease;
}

.caret-open {
    transform: rotate(0deg);
}

.caret-closed {
    transform: rotate(-90deg);
}
</style>
