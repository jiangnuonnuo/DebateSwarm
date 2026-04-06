<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { marked } from 'marked';
import hljs from 'highlight.js';
import DOMPurify from 'dompurify';
import {
    fetchComplete,
    fetchStream,
    insertSession,
    listSessions,
    listChatMessages,
    pickContentFromResult,
    dispatchArmory,
    queryChatModels,
    queryChatMcps,
    queryRagTags,
    updateSession,
    uploadRagFile,
    uploadRagGit
} from '../request/api';
import { normalizeError, notifyAppError } from '../request/request';
import { applyStreamToken, createStreamAccumulator, parseThinkText } from '../utils/StringUtil';
import { areMessageListsEqual, createStableRecordId, toSafeTimestamp } from '../utils/MessageRenderUtil';
import { useAgentStore, useChatStore, useSettingsStore, useWelcomeLaunchStore } from '../router/pinia';
import Footer from './Footer.vue';

const router = useRouter();
const chatStore = useChatStore();
const agentStore = useAgentStore();
const settingsStore = useSettingsStore();
const welcomeLaunchStore = useWelcomeLaunchStore();

const models = ref([]);
const mcpTools = ref([]);
const ragTags = ref([{ label: '不使用知识库', value: '' }]);
const selectedMcpIds = ref([]);
const pendingClientId = ref('');

const messageScrollRef = ref(null);
const modelSelectRef = ref(null);
const mcpSelectRef = ref(null);
const ragSelectRef = ref(null);
const uploadRagSelectRef = ref(null);
const inputValue = ref('');
const showSettings = ref(false);
const showUploadModal = ref(false);
const isAtBottom = ref(true);
const modelDropdownOpen = ref(false);
const mcpDropdownOpen = ref(false);
const ragDropdownOpen = ref(false);
const uploadRagDropdownOpen = ref(false);
const copiedMessageId = ref(null);
const copyTimer = ref(null);
const messageLoading = ref(false);
const sendError = ref('');
const skipNextLoadChatId = ref(null);
let autoRefreshTimer = null;

const uploadForm = reactive({
    mode: 'file',
    tagInput: '',
    selectedTag: '',
    file: null,
    fileName: '',
    fileSize: '',
    repoUrl: '',
    repoUsername: '',
    repoPassword: '',
    error: '',
    uploading: false
});

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
        messages: []
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

const listRemoteChatSessions = async (options = {}) => {
    const resp = await listSessions(options);
    const list = pickData(resp, '获取会话失败') || [];
    const mapped = (Array.isArray(list) ? list : []).map(mapSession).filter(Boolean);
    return mapped.filter((item) => item.sessionType === 'chat');
};

const refreshChatSessions = async (options = {}) => {
    const chatList = await listRemoteChatSessions(options);
    chatStore.setChats(chatList);
    return chatList;
};

const dropInvalidChatSession = (chatId = chatStore.currentChatId) => {
    if (chatId) {
        chatStore.removeChat(chatId);
    }
    if (chatStore.chats.length === 0 && agentStore.sessions.length === 0) {
        router.replace('/welcome');
    }
    sendError.value = SESSION_INVALID_HINT;
};

const ensureChatSessionValid = async (chat, options = {}) => {
    if (!chat?.sessionId) return null;
    const silentToast = Boolean(options.silentToast);
    try {
        const chatList = await listRemoteChatSessions(silentToast ? { toast: false } : {});
        const matched = chatList.find((item) => item.sessionId === chat.sessionId);
        if (!matched) {
            dropInvalidChatSession(chat.sessionId);
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
    const rawContent = message?.messageContent || message?.content || '';
    const content = parseThinkText(rawContent).answer;
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
        think: '',
        pending: false,
        error: null,
        createdAt: toSafeTimestamp(createTime)
    };
};

const normalizeSettingValue = (value) => {
    if (value === null || value === undefined || value === '') return '';
    return value;
};

const settingsForm = reactive({
    type: settingsStore.type,
    temperature: normalizeSettingValue(settingsStore.temperature),
    presencePenalty: normalizeSettingValue(settingsStore.presencePenalty),
    maxCompletionTokens: normalizeSettingValue(settingsStore.maxCompletionTokens)
});

const settingsErrors = reactive({
    temperature: '',
    presencePenalty: '',
    maxCompletionTokens: ''
});

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

const currentModel = computed({
    get: () => {
        const chat = chatStore.currentChat;
        if (chat) {
            return chat.clientId || '';
        }
        return pendingClientId.value || '';
    },
    set: (value) => {
        const chat = chatStore.currentChat;
        if (chat?.sessionId) {
            if (chatStore.isChatSelectionLocked(chat.sessionId)) {
                return;
            }
            chatStore.setChatClient(chat.sessionId, value || '');
            return;
        }
        pendingClientId.value = value || '';
    }
});
const isClientLocked = computed(() => {
    const sessionId = chatStore.currentChat?.sessionId || '';
    if (!sessionId) return false;
    return chatStore.isChatSelectionLocked(sessionId);
});

const currentModelLabel = computed(() => {
    if (!currentModel.value) {
        return models.value.length ? '选择 CLIENT' : '暂无模型';
    }
    const match = models.value.find((item) => item.value === currentModel.value);
    if (match?.label) return match.label;
    return models.value.length === 0 ? '暂无模型' : currentModel.value;
});

const currentMcpLabel = computed(() => {
    if (!selectedMcpIds.value.length) return '不使用工具';
    if (selectedMcpIds.value.length === 1) {
        const match = mcpTools.value.find((item) => item.value === selectedMcpIds.value[0]);
        return match?.label || '不使用工具';
    }
    return '多个工具';
});

const currentRagTag = computed({
    get: () => settingsStore.ragTag,
    set: (value) => settingsStore.updateSettings({ ragTag: value })
});

const messages = computed(() => chatStore.currentMessages);
const currentChatSessionId = computed(() => chatStore.currentChat?.sessionId || '');
const sending = computed(() => chatStore.sending);
const userMessageCount = computed(() => messages.value.filter((item) => item.role === 'user').length);
const currentChatTitle = computed(() => chatStore.currentChat?.title || '对话舞台');
const currentRagLabel = computed(() => ragTags.value.find((item) => item.value === currentRagTag.value)?.label || '不使用知识库');
const uploadTagLabel = computed(() => ragTags.value.find((item) => item.value === uploadForm.selectedTag)?.label || '选择标签');
const chatLimitReached = computed(() => userMessageCount.value >= 20);
const currentChatSubtitle = computed(() => {
    if (sending.value) {
        return '回答正在生成中，你仍然可以在右侧调整知识库和工具装配。';
    }
    if (messageLoading.value) {
        return '正在同步当前会话消息，让上下文保持最新。';
    }
    if (!messages.value.length) {
        return '先在右侧完成 CLIENT、工具和知识库装配，再开始这次对话。';
    }
    return `当前会话共 ${messages.value.length} 条消息，用户已发送 ${userMessageCount.value} 条。`;
});

const handleScroll = () => {
    const el = messageScrollRef.value;
    if (!el) return;
    const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
    isAtBottom.value = distance < 80;
};

const scrollToBottom = (smooth = true) => {
    nextTick(() => {
        const el = messageScrollRef.value;
        if (!el) return;
        el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
    });
};

const loadChatMessages = async (sessionId, chatId = chatStore.currentChatId, options = {}) => {
    const silent = Boolean(options?.silent);
    if (!sessionId) {
        if (chatId) {
            chatStore.setChatMessages(chatId, []);
        }
        return;
    }
    if (!silent) {
        messageLoading.value = true;
    }
    try {
        const resp = await listChatMessages({ sessionId });
        const list = pickData(resp, '获取消息失败') || [];
        const seenMap = new Map();
        const mapped = (Array.isArray(list) ? list : []).map((item) => mapMessage(item, sessionId, seenMap));
        if (chatId) {
            const existing = chatStore.chats.find((item) => item.sessionId === chatId)?.messages || [];
            if (!areMessageListsEqual(existing, mapped)) {
                chatStore.setChatMessages(chatId, mapped);
            }
        }
    } catch (error) {
        const message = normalizeError(error).message || '获取消息失败';
        if (isInvalidSessionErrorMessage(message)) {
            dropInvalidChatSession(chatId);
            return;
        }
        notifyAppError(error, '获取消息失败');
    } finally {
        if (!silent) {
            messageLoading.value = false;
        }
    }
};

const ensureChatSession = async ({ skipInitialLoad = false, forceNew = false, sessionTitle = '新会话', silentToast = false } = {}) => {
    if (!forceNew && chatStore.currentChat) {
        if (!chatStore.currentChatId && chatStore.currentChat?.sessionId) {
            chatStore.setCurrentChatId(chatStore.currentChat.sessionId);
        }
        return chatStore.currentChat;
    }
    try {
        const resp = await insertSession(
            { sessionTitle: sessionTitle || '新会话', sessionType: 'chat' },
            silentToast ? { toast: false } : {}
        );
        const created = mapSession(pickData(resp, '创建会话失败'));
        if (created) {
            let resolvedChat = created;
            chatStore.upsertChat(created);
            if (!resolvedChat.sessionId) {
                const chatList = await refreshChatSessions(silentToast ? { toast: false } : {});
                resolvedChat = chatList.find((item) => item.sessionId === created.sessionId) || resolvedChat;
            }
            if (skipInitialLoad) {
                skipNextLoadChatId.value = resolvedChat.sessionId;
            }
            chatStore.setCurrentChatId(resolvedChat.sessionId);
            if (pendingClientId.value) {
                chatStore.setChatClient(resolvedChat.sessionId, pendingClientId.value);
            }
            return resolvedChat;
        }
        const chatList = await refreshChatSessions(silentToast ? { toast: false } : {});
        const session = chatList[0] || null;
        if (!session) return null;
        if (skipInitialLoad) {
            skipNextLoadChatId.value = session.sessionId;
        }
        chatStore.setCurrentChatId(session.sessionId);
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

const renameChatIfNeeded = async (chat, content, options = {}) => {
    if (!chat) return;
    if (chat.title && chat.title !== '新会话') return;
    const silentToast = Boolean(options.silentToast);
    const nextTitle = content.slice(0, 20) || '新会话';
    chatStore.updateChatTitle(chat.sessionId, nextTitle);
    try {
        await updateSession({ sessionId: chat.sessionId, sessionTitle: nextTitle }, silentToast ? { toast: false } : {});
    } catch (error) {
        if (!silentToast) {
            notifyAppError(error, '更新会话失败');
        }
    }
};

watch(
    messages,
    () => {
        if (isAtBottom.value) scrollToBottom(true);
    },
    { deep: true }
);

watch(
    () => chatStore.currentChatId,
    async (chatId) => {
        sendError.value = '';
        const chat = chatStore.currentChat;
        if (chatId && skipNextLoadChatId.value === chatId) {
            skipNextLoadChatId.value = null;
            nextTick(() => scrollToBottom(false));
            return;
        }
        if (chat?.sessionId) {
            await loadChatMessages(chat.sessionId, chatId);
        }
        nextTick(() => scrollToBottom(false));
    },
    { immediate: true }
);

watch(
    () => inputValue.value,
    () => {
        if (sendError.value && userMessageCount.value < 20) {
            sendError.value = '';
        }
    }
);

watch(
    userMessageCount,
    (count) => {
        if (count >= 20) {
            sendError.value = '当前会话已达到 20 条用户消息上限，请新建会话';
        }
    },
    { immediate: true }
);

watch(isClientLocked, (locked) => {
    if (locked) {
        modelDropdownOpen.value = false;
    }
});

const fetchTags = async () => {
    try {
        const resp = await queryRagTags();
        const list = pickData(resp, '获取知识库标签失败') || [];
        const normalized = (Array.isArray(list) ? list : [])
            .map((item) => {
                if (typeof item === 'string') return item.trim();
                if (item && typeof item === 'object') {
                    return (item.ragTag || item.tag || item.value || '').toString().trim();
                }
                return '';
            })
            .filter(Boolean);
        const unique = Array.from(new Set(['', ...normalized]));
        ragTags.value = unique.map((item) => ({
            label: item || '不使用知识库',
            value: item || ''
        }));
        if (!unique.includes(currentRagTag.value)) {
            currentRagTag.value = '';
        }
    } catch (error) {
        console.warn('获取知识库标签失败', error);
    }
};

const fetchModels = async () => {
    try {
        const resp = await queryChatModels();
        const list = pickData(resp, '获取模型列表失败') || [];
        const normalized = list
            .map((item) => {
                if (typeof item === 'string') {
                    return { label: item, value: item, sourceType: 'system' };
                }
                if (item && typeof item === 'object') {
                    const clientId = item.clientId || item.modelId || '';
                    const modelName = item.modelName || item.name || clientId;
                    const sourceType = item.sourceType || 'system';
                    if (!clientId) return null;
                    return { label: modelName || clientId, value: clientId, sourceType };
                }
                return null;
            })
            .filter(Boolean);
        const seen = new Set();
        const unique = normalized.filter((item) => {
            if (seen.has(item.value)) return false;
            seen.add(item.value);
            return true;
        });
        if (unique.length > 0) {
            models.value = unique;
            if (currentModel.value && !unique.some((item) => item.value === currentModel.value)) {
                currentModel.value = '';
            }
        } else {
            models.value = [];
            currentModel.value = '';
        }
    } catch (error) {
        console.warn('获取模型列表失败', error);
    }
};

const fetchMcpTools = async () => {
    try {
        const resp = await queryChatMcps();
        const list = pickData(resp, '获取 MCP 工具失败') || [];
        const normalized = list
            .map((item) => {
                if (!item || typeof item !== 'object') return null;
                const mcpId = item.mcpId || '';
                const mcpName = item.mcpName || item.name || mcpId;
                const mcpDesc = item.mcpDesc || item.desc || '';
                const sourceType = item.sourceType || 'system';
                if (!mcpId) return null;
                return { label: mcpName || mcpId, value: mcpId, desc: mcpDesc, sourceType };
            })
            .filter(Boolean);
        const seen = new Set();
        const unique = normalized.filter((item) => {
            if (seen.has(item.value)) return false;
            seen.add(item.value);
            return true;
        });
        mcpTools.value = unique;
        if (selectedMcpIds.value.length) {
            const valid = new Set(unique.map((item) => item.value));
            selectedMcpIds.value = selectedMcpIds.value.filter((id) => valid.has(id));
        }
    } catch (error) {
        console.warn('获取 MCP 工具失败', error);
    }
};

const consumeWelcomeLaunchTask = async () => {
    const task = welcomeLaunchStore.takeTask('chat');
    if (!task?.prompt) return;

    if (!models.value.length) {
        await fetchModels();
    }
    if (task.clientId) {
        pendingClientId.value = task.clientId;
        if (!isClientLocked.value) {
            currentModel.value = task.clientId;
            try {
                await dispatchArmory({ armoryType: 'chat', armoryId: task.clientId });
            } catch (error) {
                console.warn('绑定 Chat armory 失败', error);
            }
        }
    } else if (!currentModel.value && models.value.length > 0) {
        currentModel.value = models.value[0].value;
    }

    if (Array.isArray(task.mcpIdList) && task.mcpIdList.length) {
        if (!mcpTools.value.length) {
            await fetchMcpTools();
        }
        const validSet = new Set(mcpTools.value.map((item) => item.value));
        selectedMcpIds.value = task.mcpIdList.filter((id) => validSet.has(id));
    } else {
        selectedMcpIds.value = [];
    }

    if (typeof task.ragTag === 'string') {
        currentRagTag.value = task.ragTag;
    } else {
        currentRagTag.value = '';
    }

    if (!currentModel.value) {
        sendError.value = '暂无可用 CLIENT，请先在后台配置 client';
        return;
    }

    await sendMessage({
        content: task.prompt,
        forceNew: true,
        sessionTitle: task.sessionTitle || '新会话',
        clientId: task.clientId || currentModel.value
    });
};

onMounted(() => {
    scrollToBottom(false);
    attachListeners();
    Promise.all([fetchTags(), fetchModels(), fetchMcpTools()]).then(async () => {
        if (chatStore.currentChat) {
            await ensureChatSessionValid(chatStore.currentChat);
        }
        await consumeWelcomeLaunchTask();
    });
    autoRefreshTimer = window.setInterval(async () => {
        if (sending.value || messageLoading.value) return;
        const session = chatStore.currentChat;
        const sessionId = session?.sessionId || '';
        if (!sessionId) return;
        await loadChatMessages(sessionId, session?.sessionId, { silent: true });
    }, 5000);
});

watch(currentModel, () => {
    fetchTags();
});

onBeforeUnmount(() => {
    chatStore.stopCurrentRequest();
    detachListeners();
    if (autoRefreshTimer) {
        window.clearInterval(autoRefreshTimer);
        autoRefreshTimer = null;
    }
    if (copyTimer.value) {
        clearTimeout(copyTimer.value);
    }
});

const handleKeydown = (event) => {
    if (event.key === 'Enter' && event.metaKey) {
        event.preventDefault();
        sendMessage();
        return;
    }
    if (event.key === 'Escape') {
        modelDropdownOpen.value = false;
        mcpDropdownOpen.value = false;
        ragDropdownOpen.value = false;
        uploadRagDropdownOpen.value = false;
    }
};

const renderMarkdown = (text) => {
    if (!text) return '';
    return DOMPurify.sanitize(marked.parse(text), { ADD_ATTR: ['class'] });
};

const copyText = async (text) => {
    if (!text) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(text);
        return;
    }
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'absolute';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
};

const handleCopy = async (message) => {
    const text = getContent(message);
    if (!text) return;
    try {
        await copyText(text);
        copiedMessageId.value = message.id;
        if (copyTimer.value) {
            clearTimeout(copyTimer.value);
        }
        copyTimer.value = setTimeout(() => {
            copiedMessageId.value = null;
        }, 1200);
    } catch (error) {
        console.warn('复制失败', error);
    }
};

const extractStreamParts = (payload) => {
    const data = payload?.result ?? payload;
    const finishReason = data?.finishReason || payload?.finishReason || null;
    if (typeof data === 'string') {
        return { token: data, finishReason };
    }
    if (typeof data === 'number' || typeof data === 'boolean') {
        return { token: String(data), finishReason };
    }
    if (!data || typeof data !== 'object') {
        return { token: '', finishReason };
    }
    if (Array.isArray(data.choices) && data.choices.length > 0) {
        const choice = data.choices[0] || {};
        const delta = choice.delta || choice.message || {};
    const answer = delta.content || delta.text || '';
    return {
        answer,
        finishReason: choice.finish_reason || finishReason,
        direct: true
    };
    }
    const answer = data.content || data.text || data.output?.content || data.output?.text || '';
    if (answer) {
        return { answer, finishReason, direct: true };
    }
    return { token: pickContentFromResult(data), finishReason };
};

const syncSettingsFormWithStore = () => {
    settingsForm.type = settingsStore.type;
    settingsForm.temperature = normalizeSettingValue(settingsStore.temperature);
    settingsForm.presencePenalty = normalizeSettingValue(settingsStore.presencePenalty);
    settingsForm.maxCompletionTokens = normalizeSettingValue(settingsStore.maxCompletionTokens);
};

const validateSettingsBeforeSend = () => {
    syncSettingsFormWithStore();
    const valid = validateSettings(true);
    if (!valid) {
        showSettings.value = true;
    }
    return valid;
};

const normalizeMarkdownSpacing = (text) => {
    if (!text) return '';

    const startsWithMarkdownSyntax = (line) => {
        const trimmed = (line || '').trimStart();
        if (!trimmed) return false;
        return /^(#{1,6}(?:\s|$)|[-*+](?:\s|$)|\d+\.(?:\s|$)|>\s?|```|~~~|\|)/.test(trimmed);
    };

    const normalizeNonCodeSegment = (segment) => {
        const lines = segment.split(/\r?\n/);
        const normalized = [];

        for (let i = 0; i < lines.length; i += 1) {
            const current = lines[i];
            const next = lines[i + 1];

            // Merge only when current line is a marker-only line AND next line is plain text.
            if (
                /^\s*(#{1,6}|[-*+]|\d+\.)\s*$/.test(current) &&
                next != null &&
                next.trim() &&
                !startsWithMarkdownSyntax(next)
            ) {
                normalized.push(`${current.replace(/\s+$/, '')} ${next.trimStart()}`);
                i += 1;
                continue;
            }

            normalized.push(
                current
                    .replace(/^(\s*)(#{1,6})(\S)/, '$1$2 $3')
                    .replace(/^(\s*)([-*+])(\S)/, '$1$2 $3')
                    .replace(/^(\s*)(\d+\.)(\S)/, '$1$2 $3')
            );
        }

        return normalized.join('\n');
    };

    return text
        .split('```')
        .map((segment, index) => {
            if (index % 2 === 1) return segment;
            return normalizeNonCodeSegment(segment);
        })
        .join('```');
};

const sendMessage = async (options = {}) => {
    const content = (options.content ?? inputValue.value).trim();
    const requestedClientId = (options.clientId || '').trim();
    const resolvedClientId = requestedClientId || currentModel.value;
    if (!content || sending.value || !resolvedClientId) return;
    if (!validateSettingsBeforeSend()) return;
    sendError.value = '';
    const forceNew = Boolean(options.forceNew);
    const chat = await ensureChatSession({
        skipInitialLoad: forceNew || !chatStore.currentChat,
        forceNew,
        sessionTitle: options.sessionTitle || '新会话',
        silentToast: true
    });
    if (!chat) return;
    const validChat = await ensureChatSessionValid(chat, { silentToast: true });
    if (!validChat) return;
    if (validChat?.sessionId) {
        chatStore.setChatClient(validChat.sessionId, resolvedClientId);
    }
    if (userMessageCount.value >= 20) {
        sendError.value = '当前会话已达到 20 条用户消息上限，请新建会话';
        return;
    }
    const sessionId = validChat.sessionId || currentChatSessionId.value;
    if (!sessionId) {
        sendError.value = '会话初始化中，请稍后重试';
        return;
    }
    const mode = settingsStore.type || 'complete';
    chatStore.stopCurrentRequest();
    chatStore.setSending(true);
    const controller = new AbortController();
    chatStore.setAbortController(controller);
    chatStore.setChatSelectionLocked(sessionId, true);
    modelDropdownOpen.value = false;
    chatStore.addUserMessage(content);
    renameChatIfNeeded(validChat, content, { silentToast: true });
    if (!Object.prototype.hasOwnProperty.call(options, 'content')) {
        inputValue.value = '';
    }
    scrollToBottom(true);
    if (mode === 'stream') {
        await runStream(content, controller, sessionId, resolvedClientId);
    } else {
        await runComplete(content, controller, sessionId, resolvedClientId);
    }
};

const buildChatRequestPayload = (userMessage, sessionId, clientId) => ({
    clientId: clientId || currentModel.value,
    userMessage,
    temperature: settingsStore.temperature ?? undefined,
    presencePenalty: settingsStore.presencePenalty ?? undefined,
    maxCompletionTokens: settingsStore.maxCompletionTokens ?? undefined,
    mcpIdList: selectedMcpIds.value.length ? [...selectedMcpIds.value] : [],
    sessionId,
    ragTag: currentRagTag.value
});

const runComplete = async (content, controller, sessionId, clientId) => {
    const assistantMessage = chatStore.addAssistantMessage({ pending: true, content: '', think: '' });
    try {
        const response = await fetchComplete({
            ...buildChatRequestPayload(content, sessionId, clientId),
            signal: controller.signal
        });
        const text = pickContentFromResult(response);
        const { answer } = parseThinkText(text);
        const normalized = normalizeMarkdownSpacing(answer);
        chatStore.updateAssistantMessage(assistantMessage.id, {
            content: normalized || '（无内容）',
            think: '',
            pending: false,
            error: null
        });
    } catch (error) {
        const friendly = normalizeError(error);
        const stopped = friendly.message && friendly.message.includes('取消');
        chatStore.updateAssistantMessage(assistantMessage.id, {
            content: stopped ? '已停止生成' : friendly.message || '请求失败',
            think: '',
            pending: false,
            error: friendly
        });
    } finally {
        chatStore.setSending(false);
        chatStore.setAbortController(null);
        scrollToBottom(true);
    }
};

const runStream = async (content, controller, sessionId, clientId) => {
    const accumulator = createStreamAccumulator();
    const assistantMessage = chatStore.addAssistantMessage({ pending: true, content: '', think: '' });
    let directRaw = '';
    let closed = false;

    const finishStream = () => {
        if (closed) return;
        closed = true;
        if (accumulator.carry) {
            if (accumulator.inThink) {
                // discard remaining think fragment
            } else {
                accumulator.answer += accumulator.carry;
            }
            accumulator.carry = '';
        }
        const normalized = normalizeMarkdownSpacing(parseThinkText(accumulator.answer).answer);
        chatStore.updateAssistantMessage(assistantMessage.id, {
            content: normalized,
            think: '',
            pending: false
        });
        chatStore.setSending(false);
        chatStore.setAbortController(null);
        scrollToBottom(true);
    };

    const handleError = (error) => {
        if (closed) return;
        const friendly = normalizeError(error);
        if (friendly.message && friendly.message.toLowerCase().includes('取消')) {
            finishStream();
            return;
        }
        closed = true;
        chatStore.updateAssistantMessage(assistantMessage.id, {
            content: friendly.message || '请求失败',
            think: '',
            pending: false,
            error: friendly
        });
        chatStore.setSending(false);
        chatStore.setAbortController(null);
        scrollToBottom(true);
    };

    try {
        await fetchStream({
            ...buildChatRequestPayload(content, sessionId, clientId),
            signal: controller.signal,
            onData: (payload) => {
                const { token, answer, direct, finishReason } = extractStreamParts(payload);
                if (direct) {
                    if (answer) {
                        const chunk = String(answer);
                        if (directRaw && chunk.startsWith(directRaw)) {
                            directRaw = chunk;
                        } else {
                            directRaw += chunk;
                        }
                        accumulator.answer = parseThinkText(directRaw).answer;
                        accumulator.carry = '';
                        accumulator.inThink = false;
                    }
                } else if (token) {
                    applyStreamToken(accumulator, token);
                }
                if (direct || token) {
                    chatStore.updateAssistantMessage(assistantMessage.id, {
                        content: accumulator.answer,
                        think: '',
                        pending: true,
                        error: null
                    });
                    if (isAtBottom.value) scrollToBottom(true);
                }
                if (finishReason === 'stop') finishStream();
            },
            onError: handleError,
            onDone: finishStream
        });
    } catch (error) {
        handleError(error);
    }
};

const handleStop = () => {
    if (!sending.value) return;
    chatStore.stopCurrentRequest();
    const lastAssistant = [...messages.value].filter((item) => item.role === 'assistant').pop();
    if (lastAssistant && lastAssistant.pending) {
        chatStore.updateAssistantMessage(lastAssistant.id, {
            pending: false,
            error: { message: '已停止生成' }
        });
    }
};

const parseOptionalNumber = (value, options) => {
    if (value === '' || value === null || value === undefined) {
        return { value: null, error: '' };
    }
    const num = Number(value);
    if (Number.isNaN(num)) {
        return { value: null, error: `${options.label} 请输入数字` };
    }
    if (options.integer && !Number.isInteger(num)) {
        return { value: null, error: `${options.label} 必须是整数` };
    }
    if (num < options.min || num > options.max) {
        return { value: null, error: `${options.label} 取值范围 ${options.min} ~ ${options.max}` };
    }
    return { value: num, error: '' };
};

const validateSettings = (showErrors = true) => {
    const temperatureResult = parseOptionalNumber(settingsForm.temperature, {
        label: 'temperature',
        min: 0,
        max: 1,
        integer: false
    });
    const presenceResult = parseOptionalNumber(settingsForm.presencePenalty, {
        label: 'presence penalty',
        min: 0,
        max: 1,
        integer: false
    });
    const maxTokenResult = parseOptionalNumber(settingsForm.maxCompletionTokens, {
        label: 'max token',
        min: 1,
        max: 8192,
        integer: true
    });

    if (showErrors) {
        settingsErrors.temperature = temperatureResult.error;
        settingsErrors.presencePenalty = presenceResult.error;
        settingsErrors.maxCompletionTokens = maxTokenResult.error;
    }

    return !temperatureResult.error && !presenceResult.error && !maxTokenResult.error;
};

const buildChatSettingsPayload = () => {
    const temperatureResult = parseOptionalNumber(settingsForm.temperature, {
        label: 'temperature',
        min: 0,
        max: 1,
        integer: false
    });
    const presenceResult = parseOptionalNumber(settingsForm.presencePenalty, {
        label: 'presence penalty',
        min: 0,
        max: 1,
        integer: false
    });
    const maxTokenResult = parseOptionalNumber(settingsForm.maxCompletionTokens, {
        label: 'max token',
        min: 1,
        max: 8192,
        integer: true
    });

    return {
        temperature: temperatureResult.value ?? undefined,
        presencePenalty: presenceResult.value ?? undefined,
        maxCompletionTokens: maxTokenResult.value ?? undefined
    };
};

const openSettings = () => {
    syncSettingsFormWithStore();
    settingsErrors.temperature = '';
    settingsErrors.presencePenalty = '';
    settingsErrors.maxCompletionTokens = '';
    showSettings.value = true;
};

const saveSettings = () => {
    if (!validateSettings(true)) {
        return;
    }
    const payload = buildChatSettingsPayload();
    settingsStore.updateSettings({
        type: settingsForm.type,
        temperature: payload.temperature ?? null,
        presencePenalty: payload.presencePenalty ?? null,
        maxCompletionTokens: payload.maxCompletionTokens ?? null
    });
    showSettings.value = false;
};

const getContent = (message) => (message?.content ? message.content.toString() : '');

const toggleModelDropdown = () => {
    if (models.value.length === 0 || isClientLocked.value) return;
    mcpDropdownOpen.value = false;
    ragDropdownOpen.value = false;
    modelDropdownOpen.value = !modelDropdownOpen.value;
};

const toggleMcpDropdown = () => {
    modelDropdownOpen.value = false;
    ragDropdownOpen.value = false;
    mcpDropdownOpen.value = !mcpDropdownOpen.value;
};

const toggleMcpSelection = (value) => {
    const idx = selectedMcpIds.value.indexOf(value);
    if (idx >= 0) {
        selectedMcpIds.value.splice(idx, 1);
    } else {
        selectedMcpIds.value.push(value);
    }
};

const selectModel = async (value) => {
    if (isClientLocked.value) return;
    currentModel.value = value;
    modelDropdownOpen.value = false;
    try {
        await dispatchArmory({ armoryType: 'chat', armoryId: value });
    } catch (error) {
        console.warn('绑定 Chat armory 失败', error);
    }
};

const toggleRagDropdown = () => {
    modelDropdownOpen.value = false;
    mcpDropdownOpen.value = false;
    ragDropdownOpen.value = !ragDropdownOpen.value;
};

const selectRag = (value) => {
    currentRagTag.value = value;
    uploadForm.tagInput = value;
    uploadForm.selectedTag = value;
    ragDropdownOpen.value = false;
};

const toggleUploadRagDropdown = () => {
    uploadRagDropdownOpen.value = !uploadRagDropdownOpen.value;
};

const selectUploadRag = (value) => {
    uploadForm.selectedTag = value;
    uploadForm.tagInput = value;
    uploadRagDropdownOpen.value = false;
};

const handleClickOutside = (event) => {
    const target = event.target;
    const inModel = modelSelectRef.value && modelSelectRef.value.contains(target);
    const inMcp = mcpSelectRef.value && mcpSelectRef.value.contains(target);
    const inRag = ragSelectRef.value && ragSelectRef.value.contains(target);
    const inUploadRag = uploadRagSelectRef.value && uploadRagSelectRef.value.contains(target);
    if (!inModel) modelDropdownOpen.value = false;
    if (!inMcp) mcpDropdownOpen.value = false;
    if (!inRag) ragDropdownOpen.value = false;
    if (!inUploadRag) uploadRagDropdownOpen.value = false;
};

const handleEscClose = (event) => {
    if (event.key === 'Escape') {
        modelDropdownOpen.value = false;
        mcpDropdownOpen.value = false;
        ragDropdownOpen.value = false;
        uploadRagDropdownOpen.value = false;
    }
};

const attachListeners = () => {
    document.addEventListener('click', handleClickOutside);
    window.addEventListener('keydown', handleEscClose);
};

const detachListeners = () => {
    document.removeEventListener('click', handleClickOutside);
    window.removeEventListener('keydown', handleEscClose);
};

const openUpload = () => {
    uploadForm.mode = 'file';
    uploadForm.tagInput = currentRagTag.value || '';
    uploadForm.selectedTag = currentRagTag.value || '';
    uploadForm.file = null;
    uploadForm.fileName = '';
    uploadForm.fileSize = '';
    uploadForm.repoUrl = '';
    uploadForm.repoUsername = '';
    uploadForm.repoPassword = '';
    uploadForm.error = '';
    uploadForm.uploading = false;
    uploadRagDropdownOpen.value = false;
    showUploadModal.value = true;
};

const closeUpload = () => {
    showUploadModal.value = false;
    uploadForm.mode = 'file';
    uploadForm.tagInput = '';
    uploadForm.selectedTag = '';
    uploadForm.file = null;
    uploadForm.fileName = '';
    uploadForm.fileSize = '';
    uploadForm.repoUrl = '';
    uploadForm.repoUsername = '';
    uploadForm.repoPassword = '';
    uploadForm.error = '';
    uploadForm.uploading = false;
};

const formatSize = (size) => {
    if (!size && size !== 0) return '';
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
};

const handleFileChange = (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    const lowerName = file.name.toLowerCase();
    const isTxt = lowerName.endsWith('.txt');
    const isMd = lowerName.endsWith('.md');
    if (!isTxt && !isMd) {
        uploadForm.error = '仅支持 .txt 或 .md 文件';
        uploadForm.file = null;
        uploadForm.fileName = '';
        uploadForm.fileSize = '';
        event.target.value = '';
        return;
    }
    uploadForm.error = '';
    uploadForm.file = file;
    uploadForm.fileName = file.name;
    uploadForm.fileSize = formatSize(file.size);
};

const resolveUploadTag = () => {
    const input = uploadForm.tagInput.trim();
    if (input) return input;
    return uploadForm.selectedTag || '';
};

const handleUpload = async () => {
    uploadForm.error = '';
    if (uploadForm.mode === 'file') {
        const tag = resolveUploadTag();
        if (!tag) {
            uploadForm.error = '请选择或输入知识库标签';
            return;
        }
        if (!uploadForm.file) {
            uploadForm.error = '请选择 txt 或 md 文件';
            return;
        }
        uploadForm.uploading = true;
        try {
            const resp = await uploadRagFile({ ragTag: tag, file: uploadForm.file });
            pickData(resp, '上传失败');
            await fetchTags();
            currentRagTag.value = tag;
            selectRag(tag);
            closeUpload();
        } catch (error) {
            const friendly = normalizeError(error);
            uploadForm.error = friendly.message || '上传失败，请重试';
        } finally {
            uploadForm.uploading = false;
        }
        return;
    }

    const repo = uploadForm.repoUrl.trim();
    const username = uploadForm.repoUsername.trim();
    const password = uploadForm.repoPassword;
    if (!repo) {
        uploadForm.error = '请输入 Git 仓库地址';
        return;
    }
    if (!username) {
        uploadForm.error = '请输入 Git 用户名';
        return;
    }
    if (!password) {
        uploadForm.error = '请输入 Git 密码';
        return;
    }
    uploadForm.uploading = true;
    try {
        const resp = await uploadRagGit({ repoUrl: repo, username, password });
        pickData(resp, '上传失败');
        await fetchTags();
        const repoName = repo.split('/').pop()?.replace(/\.git$/i, '') || '';
        if (repoName) {
            currentRagTag.value = repoName;
            selectRag(repoName);
        }
        closeUpload();
    } catch (error) {
        const friendly = normalizeError(error);
        uploadForm.error = friendly.message || '上传失败，请重试';
    } finally {
        uploadForm.uploading = false;
    }
};
</script>

<template>
    <section class="grid h-screen grid-rows-[auto_1fr_auto_var(--footer-height)] bg-[var(--bg-page)]">
        <header class="sticky top-0 z-30 h-[var(--header-height)] border-b border-[var(--border-color)] bg-[var(--panel-bg)] backdrop-blur-xl">
            <div class="mx-auto flex h-full w-full max-w-[var(--workspace-max)] items-center justify-between px-[24px] max-[720px]:px-[12px]">
                <div class="flex min-w-0 items-center gap-[18px]">
                    <div class="min-w-0">
                        <h2 class="truncate text-[18px] font-bold tracking-tight text-[var(--text-primary)]">{{ currentChatTitle }}</h2>
                    </div>
                    
                    <div class="ml-[12px] flex items-center gap-[8px] max-[1024px]:hidden">
                        <div class="relative">
                            <button
                                ref="modelSelectRef"
                                type="button"
                                class="inline-flex items-center gap-[6px] rounded-[10px] px-[10px] py-[6px] text-[12px] font-semibold transition-all hover:bg-[var(--accent-soft)]"
                                :class="models.length === 0 || isClientLocked ? 'cursor-not-allowed opacity-50' : 'text-[var(--accent-color)]'"
                                @click="toggleModelDropdown"
                            >
                                <span class="opacity-60">CLIENT:</span>
                                <span class="max-w-[120px] truncate">{{ currentModelLabel }}</span>
                                <span class="caret h-[4px] w-[4px] border-b-2 border-r-2 border-current opacity-40 transition-transform" :class="modelDropdownOpen ? 'rotate-[225deg]' : 'rotate-[45deg]'" />
                            </button>
                            <div
                                v-if="modelDropdownOpen && models.length > 0"
                                class="absolute left-0 top-[calc(100%+6px)] z-[50] w-[220px] rounded-[14px] border border-[var(--border-color)] bg-white p-[5px] shadow-[var(--panel-shadow)]"
                            >
                                <div
                                    v-for="item in models"
                                    :key="item.value"
                                    class="flex cursor-pointer items-center justify-between rounded-[8px] px-[10px] py-[8px] text-[13px] text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-page)]"
                                    :class="item.value === currentModel ? 'bg-[var(--accent-soft)] text-[var(--accent-color)] font-bold' : ''"
                                    @click.stop="selectModel(item.value)"
                                >
                                    <span class="truncate">{{ item.label }}</span>
                                    <span v-if="item.value === currentModel">✓</span>
                                </div>
                            </div>
                        </div>

                        <span class="h-[12px] w-[1px] bg-[var(--border-color)]"></span>

                        <div class="relative">
                            <button
                                ref="mcpSelectRef"
                                type="button"
                                class="inline-flex items-center gap-[6px] rounded-[10px] px-[10px] py-[6px] text-[12px] font-semibold text-[var(--text-secondary)] transition-all hover:bg-[var(--surface-3)]"
                                @click="toggleMcpDropdown"
                            >
                                <span class="opacity-60">MCP:</span>
                                <span class="max-w-[120px] truncate">{{ currentMcpLabel }}</span>
                                <span class="caret h-[4px] w-[4px] border-b-2 border-r-2 border-current opacity-40 transition-transform" :class="mcpDropdownOpen ? 'rotate-[225deg]' : 'rotate-[45deg]'" />
                            </button>
                            <div
                                v-if="mcpDropdownOpen"
                                class="absolute left-0 top-[calc(100%+6px)] z-[50] w-[220px] rounded-[14px] border border-[var(--border-color)] bg-white p-[5px] shadow-[var(--panel-shadow)] max-h-[300px] overflow-y-auto"
                                @click.stop
                            >
                                <div
                                    v-for="item in mcpTools"
                                    :key="item.value"
                                    class="flex cursor-pointer items-center justify-between rounded-[8px] px-[10px] py-[8px] text-[13px] text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-page)]"
                                    :class="selectedMcpIds.includes(item.value) ? 'bg-[var(--accent-soft)] text-[var(--accent-color)] font-bold' : ''"
                                    @click.stop="toggleMcpSelection(item.value)"
                                >
                                    <span class="truncate">{{ item.label }}</span>
                                    <span v-if="selectedMcpIds.includes(item.value)">✓</span>
                                </div>
                            </div>
                        </div>

                        <span class="h-[12px] w-[1px] bg-[var(--border-color)]"></span>

                        <div class="relative">
                            <button
                                ref="ragSelectRef"
                                type="button"
                                class="inline-flex items-center gap-[6px] rounded-[10px] px-[10px] py-[6px] text-[12px] font-semibold text-[var(--text-secondary)] transition-all hover:bg-[var(--surface-3)]"
                                @click="toggleRagDropdown"
                            >
                                <span class="opacity-60">RAG:</span>
                                <span class="max-w-[120px] truncate">{{ currentRagLabel }}</span>
                                <span class="caret h-[4px] w-[4px] border-b-2 border-r-2 border-current opacity-40 transition-transform" :class="ragDropdownOpen ? 'rotate-[225deg]' : 'rotate-[45deg]'" />
                            </button>
                            <div
                                v-if="ragDropdownOpen"
                                class="absolute left-0 top-[calc(100%+6px)] z-[50] w-[220px] rounded-[14px] border border-[var(--border-color)] bg-white p-[5px] shadow-[var(--panel-shadow)] max-h-[300px] overflow-y-auto"
                            >
                                <div
                                    v-for="item in ragTags"
                                    :key="item.value || 'empty'"
                                    class="flex cursor-pointer items-center justify-between rounded-[8px] px-[10px] py-[8px] text-[13px] text-[var(--text-primary)] transition-colors hover:bg-[var(--bg-page)]"
                                    :class="item.value === currentRagTag ? 'bg-[var(--accent-soft)] text-[var(--accent-color)] font-bold' : ''"
                                    @click.stop="selectRag(item.value)"
                                >
                                    <span class="truncate">{{ item.label }}</span>
                                    <span v-if="item.value === currentRagTag">✓</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="flex items-center gap-[12px]">
                    <button
                        class="inline-flex h-[36px] items-center justify-center rounded-[10px] border border-[var(--border-color)] bg-white/60 px-[14px] text-[13px] font-semibold text-[var(--text-primary)] transition-all hover:bg-white hover:shadow-sm active:scale-95"
                        type="button"
                        @click="openUpload"
                    >
                        上传知识库
                    </button>
                    <button
                        class="inline-flex h-[36px] items-center justify-center rounded-[10px] bg-[var(--accent-color)] px-[14px] text-[13px] font-semibold text-white shadow-sm shadow-blue-500/20 transition-all hover:brightness-110 active:scale-95"
                        type="button"
                        @click="openSettings"
                    >
                        回答设置
                    </button>
                </div>
            </div>
        </header>

        <div class="overflow-hidden bg-[var(--bg-page)]">
            <div class="mx-auto grid h-full max-w-[var(--workspace-max)] grid-cols-1 gap-0 px-[24px] py-[18px] max-[720px]:px-[12px]">
                <div class="panel-surface flex min-h-0 flex-col overflow-hidden rounded-[24px] border border-[var(--panel-border)] shadow-[var(--panel-shadow)]">
                    <div
                        ref="messageScrollRef"
                        class="flex-1 overflow-y-auto px-[24px] py-[24px] scroll-smooth [scrollbar-gutter:auto] max-[720px]:px-[14px]"
                        @scroll="handleScroll"
                    >
                        <div class="mx-auto flex w-full max-w-[960px] flex-col gap-[20px]">
                            <div v-if="messageLoading" class="notice-inline">加载会话消息中...</div>
                            <div v-else-if="messages.length === 0" class="empty-state min-h-[400px] flex flex-col items-center justify-center text-center">
                                <div class="mb-[20px] opacity-20 text-[var(--accent-color)]">
                                    <svg class="h-[64px] w-[64px]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                                    </svg>
                                </div>
                                <div class="text-[16px] font-semibold text-[var(--text-primary)]">开始一次新的对话</div>
                                <div class="mt-[8px] max-w-[400px] text-[14px] leading-[1.6] text-[var(--text-secondary)]">
                                    {{ currentChatSubtitle }}
                                </div>
                            </div>
                            <div
                                v-for="message in messages"
                                :key="message.id"
                                class="flex w-full"
                                :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
                            >
                                <div class="flex max-w-[90%] gap-[12px]" :class="message.role === 'user' ? 'flex-row-reverse' : 'flex-row'">
                                    <div class="flex min-w-0 flex-col gap-[6px]" :class="message.role === 'user' ? 'items-end' : 'items-start'">
                                        <div
                                            class="relative w-full overflow-hidden rounded-[20px] px-[18px] py-[14px]"
                                            :class="[
                                                message.error
                                                    ? 'bg-[var(--notice-bg)] border border-[var(--notice-border)] text-[var(--notice-text)]'
                                                    : message.role === 'user'
                                                        ? 'bg-[var(--accent-color)] text-white shadow-md shadow-blue-500/10'
                                                        : 'bg-white border border-[var(--panel-border)] shadow-sm text-[var(--text-primary)]',
                                            ]"
                                        >
                                            <div
                                                v-if="message.pending && message.role === 'assistant' && !message.content"
                                                class="inline-flex items-center gap-[8px]"
                                            >
                                                <div class="inline-flex items-center gap-[4px]">
                                                    <span class="h-[5px] w-[5px] rounded-full bg-blue-400 animate-bounce"></span>
                                                    <span class="h-[5px] w-[5px] rounded-full bg-blue-400 animate-bounce [animation-delay:0.2s]"></span>
                                                    <span class="h-[5px] w-[5px] rounded-full bg-blue-400 animate-bounce [animation-delay:0.4s]"></span>
                                                </div>
                                                <div class="text-[13px] font-medium text-[var(--accent-color)]">思考中...</div>
                                            </div>
                                            <div
                                                v-else-if="message.role === 'user' || message.pending"
                                                class="whitespace-pre-wrap break-all text-[15px] leading-[1.7] [overflow-wrap:anywhere]"
                                            >
                                                {{ getContent(message) }}
                                            </div>
                                            <div
                                                v-else
                                                class="markdown-body break-words text-[15px] leading-[1.7] [overflow-wrap:anywhere] [&_pre]:overflow-auto [&_pre]:rounded-[12px] [&_pre]:bg-[#1e293b] [&_pre]:p-[14px] [&_pre]:text-[#f1f5f9] [&_code]:rounded-[4px] [&_code]:bg-[var(--surface-3)] [&_code]:px-[4px] [&_code]:py-[1px] [&_pre_code]:bg-transparent [&_pre_code]:p-0"
                                                v-html="renderMarkdown(getContent(message))"
                                            ></div>
                                        </div>
                                        <div class="flex items-center gap-[10px] px-[6px]">
                                            <span class="text-[11px] font-medium text-[var(--text-muted)]">{{ message.role === 'user' ? 'YOU' : 'AI' }}</span>
                                            <button
                                                type="button"
                                                class="opacity-0 group-hover:opacity-100 flex h-[24px] w-[24px] items-center justify-center rounded-[6px] text-[var(--text-muted)] hover:text-[var(--accent-color)] hover:bg-[var(--accent-soft)] transition-all"
                                                @click.stop="handleCopy(message)"
                                            >
                                                <svg viewBox="0 0 24 24" class="h-[12px] w-[12px]" fill="none" stroke="currentColor" stroke-width="2">
                                                    <path d="M8 8h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2Z" />
                                                    <path d="M6 16H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                                                </svg>
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="bg-[var(--bg-page)]">
            <div class="mx-auto flex w-full max-w-[1440px] flex-col gap-0 px-[24px] pb-[12px] max-[720px]:px-[12px]">
                <div class="panel-surface flex flex-col gap-[12px] rounded-[30px] p-[16px]">
                    <textarea
                        v-model="inputValue"
                        class="conversation-textarea min-h-[120px] disabled:bg-[#f4f6fb]"
                        rows="4"
                        placeholder="输入问题，Enter 发送，Shift+Enter 换行"
                        :disabled="sending"
                        @keydown="handleKeydown"
                    ></textarea>
                    <div v-if="sendError" class="notice-inline text-[13px]">
                        {{ sendError }}
                    </div>
                    <div v-else-if="chatLimitReached" class="notice-inline text-[13px]">
                        当前会话已达到 20 条用户消息上限，请新建会话继续。
                    </div>
                    <div class="flex flex-wrap items-center justify-between gap-[12px]">
                        <div class="text-[12px] leading-[1.7] text-[var(--text-secondary)]">
                            Enter 发送，Shift+Enter 换行。右侧仍可继续切换工具和知识库。
                        </div>
                        <div class="flex flex-wrap justify-end gap-[10px]">
                            <button
                                class="inline-flex min-h-[42px] items-center justify-center rounded-[16px] border border-[rgba(148,163,184,0.18)] bg-white px-[16px] py-[10px] text-[14px] font-semibold text-[var(--text-primary)] transition-all duration-200 hover:bg-[#f7f9fc] disabled:cursor-not-allowed disabled:opacity-70"
                                type="button"
                                :disabled="!sending"
                                @click="handleStop"
                            >
                                停止生成
                            </button>
                            <button
                                class="inline-flex min-h-[42px] items-center justify-center rounded-[16px] border border-[var(--accent-color)] bg-[var(--accent-color)] px-[18px] py-[10px] text-[14px] font-semibold text-white transition-all duration-200 hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-70"
                                type="button"
                                :disabled="sending || !inputValue.trim() || chatLimitReached"
                                @click="sendMessage"
                            >
                                {{ sending ? '生成中…' : '发送' }}
                            </button>
                        </div>
                    </div>
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
                        <label class="font-semibold text-[var(--text-primary)]">模式</label>
                        <div class="flex gap-[12px]">
                            <label class="flex cursor-pointer items-center gap-[6px] rounded-[12px] border border-[var(--border-color)] bg-[#f7f9fc] px-[12px] py-[10px]">
                                <input v-model="settingsForm.type" type="radio" value="complete" />
                                <span>complete</span>
                            </label>
                            <label class="flex cursor-pointer items-center gap-[6px] rounded-[12px] border border-[var(--border-color)] bg-[#f7f9fc] px-[12px] py-[10px]">
                                <input v-model="settingsForm.type" type="radio" value="stream" />
                                <span>stream</span>
                            </label>
                        </div>
                    </div>
                    <div class="flex flex-col gap-[8px]">
                        <label for="temperature" class="font-semibold text-[var(--text-primary)]">temperature（随机性）</label>
                        <input
                            id="temperature"
                            v-model="settingsForm.temperature"
                            type="number"
                            min="0"
                            max="1"
                            step="0.1"
                            class="rounded-[12px] border border-[var(--border-color)] px-[12px] py-[10px] text-[14px]"
                            @input="settingsErrors.temperature = ''"
                            @blur="validateSettings(true)"
                        />
                        <div v-if="settingsErrors.temperature" class="text-[12px] text-[var(--accent-strong)] font-medium">
                            {{ settingsErrors.temperature }}
                        </div>
                    </div>
                    <div class="flex flex-col gap-[8px]">
                        <label for="presencePenalty" class="font-semibold text-[var(--text-primary)]">presence penalty（减少重复）</label>
                        <input
                            id="presencePenalty"
                            v-model="settingsForm.presencePenalty"
                            type="number"
                            min="0"
                            max="1"
                            step="0.1"
                            class="rounded-[12px] border border-[var(--border-color)] px-[12px] py-[10px] text-[14px]"
                            @input="settingsErrors.presencePenalty = ''"
                            @blur="validateSettings(true)"
                        />
                        <div v-if="settingsErrors.presencePenalty" class="text-[12px] text-[var(--accent-strong)] font-medium">
                            {{ settingsErrors.presencePenalty }}
                        </div>
                    </div>
                    <div class="flex flex-col gap-[8px]">
                        <label for="maxTokens" class="font-semibold text-[var(--text-primary)]">max token（回复长度）</label>
                        <input
                            id="maxTokens"
                            v-model="settingsForm.maxCompletionTokens"
                            type="number"
                            min="1"
                            max="8192"
                            step="1"
                            class="rounded-[12px] border border-[var(--border-color)] px-[12px] py-[10px] text-[14px]"
                            @input="settingsErrors.maxCompletionTokens = ''"
                            @blur="validateSettings(true)"
                        />
                        <div v-if="settingsErrors.maxCompletionTokens" class="text-[12px] text-[var(--accent-strong)] font-medium">
                            {{ settingsErrors.maxCompletionTokens }}
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

        <div v-if="showUploadModal" class="fixed inset-0 z-[20] grid place-items-center bg-[rgba(0,0,0,0.35)] p-[20px]" @click.self="closeUpload">
            <div class="w-full max-w-[640px] rounded-[16px] border border-[var(--border-color)] bg-white shadow-[0_20px_50px_rgba(15,23,42,0.2)]">
                <div class="flex items-center justify-between border-b border-[var(--border-color)] px-[18px] pt-[14px] pb-[10px]">
                    <div class="text-[18px] font-bold">上传知识库</div>
                    <button class="text-[22px] text-[var(--text-secondary)]" type="button" @click="closeUpload">×</button>
                </div>
                <div class="flex flex-col gap-[14px] px-[18px] py-[14px]">
                    <div class="flex gap-[10px]">
                        <button
                            class="rounded-[10px] border border-[var(--border-color)] bg-[#f7f9fc] px-[12px] py-[8px] font-semibold text-[var(--text-secondary)] outline-none focus:outline-none focus:ring-0 focus-visible:ring-0"
                            :class="uploadForm.mode === 'file' ? 'border-[#4f8cff] text-[var(--accent-color)]' : ''"
                            :style="uploadForm.mode === 'file' ? { backgroundColor: 'var(--accent-soft)', boxShadow: 'none', outline: 'none' } : {}"
                            type="button"
                            @click="uploadForm.mode = 'file'"
                        >
                            上传文件
                        </button>
                        <button
                            class="rounded-[10px] border border-[var(--border-color)] bg-[#f7f9fc] px-[12px] py-[8px] font-semibold text-[var(--text-secondary)] outline-none focus:outline-none focus:ring-0 focus-visible:ring-0"
                            :class="uploadForm.mode === 'git' ? 'border-[#4f8cff] text-[var(--accent-color)]' : ''"
                            :style="uploadForm.mode === 'git' ? { backgroundColor: 'var(--accent-soft)', boxShadow: 'none', outline: 'none' } : {}"
                            type="button"
                            @click="uploadForm.mode = 'git'"
                        >
                            上传 Git 仓库
                        </button>
                    </div>

                    <div v-if="uploadForm.mode === 'file'" class="flex flex-col gap-[8px]">
                        <label class="font-semibold text-[var(--text-primary)]">知识库标签</label>
                        <div class="flex items-center gap-[10px]">
                            <div class="relative flex items-center gap-[8px] font-semibold">
                                <div
                                    ref="uploadRagSelectRef"
                                    class="inline-flex min-h-[36px] min-w-[160px] cursor-pointer items-center justify-between gap-[10px] rounded-[12px] border border-[var(--border-color)] bg-white px-[12px] py-[8px] shadow-[0_12px_30px_rgba(27,36,55,0.08)]"
                                    @click.stop="toggleUploadRagDropdown"
                                >
                                    <span class="font-bold text-[var(--text-primary)]">
                                        {{ uploadTagLabel }}
                                    </span>
                                    <span
                                        class="text-[var(--text-secondary)] transition-transform duration-200"
                                        :class="{ 'rotate-180': uploadRagDropdownOpen }"
                                    >
                                        ⌄
                                    </span>
                                </div>
                                <div
                                    v-if="uploadRagDropdownOpen"
                                    class="absolute left-0 top-[calc(100%+6px)] z-[15] w-full rounded-[12px] border border-[var(--border-color)] bg-white p-[6px] shadow-[0_18px_40px_rgba(15,23,42,0.12)] max-h-[240px] overflow-y-auto"
                                >
                                    <div
                                        v-for="item in ragTags"
                                        :key="item.value || 'empty-upload'"
                                        class="flex cursor-pointer items-center justify-between rounded-[10px] px-[12px] py-[10px] text-[var(--text-primary)] transition-colors duration-150 hover:bg-[#f5f7fb]"
                                        :class="item.value === uploadForm.selectedTag ? 'bg-[#e8f1ff] text-[var(--accent-color)] font-bold' : ''"
                                        @click.stop="() => selectUploadRag(item.value)"
                                    >
                                        <span>{{ item.label }}</span>
                                        <span v-if="item.value === uploadForm.selectedTag" class="text-[13px]">✓</span>
                                    </div>
                                </div>
                            </div>
                            <input
                                v-model="uploadForm.tagInput"
                                type="text"
                                class="flex-1 rounded-[12px] border border-[var(--border-color)] px-[12px] py-[10px] text-[14px]"
                                placeholder="或输入新标签"
                            />
                        </div>
                    </div>

                    <div v-if="uploadForm.mode === 'file'" class="flex flex-col gap-[8px]">
                        <label class="font-semibold text-[var(--text-primary)]">上传文件（仅 .txt / .md）</label>
                        <label class="block cursor-pointer rounded-[12px] border border-dashed border-[var(--border-color)] bg-[#f8fafc] px-[12px] py-[14px]">
                            <input
                                type="file"
                                accept=".txt,.md,text/plain,text/markdown"
                                class="hidden"
                                @change="handleFileChange"
                            />
                            <div v-if="uploadForm.fileName" class="flex items-center justify-between font-semibold text-[var(--text-primary)]">
                                <span class="name">{{ uploadForm.fileName }}</span>
                                <span class="text-[13px] text-[var(--text-secondary)]">{{ uploadForm.fileSize }}</span>
                            </div>
                            <div v-else class="text-[14px] text-[var(--text-secondary)]">点击选择或拖拽 TXT / MD 文件</div>
                        </label>
                    </div>

                    <div v-if="uploadForm.mode === 'git'" class="flex flex-col gap-[8px]">
                        <label class="font-semibold text-[var(--text-primary)]">Git 仓库地址</label>
                        <input
                            v-model="uploadForm.repoUrl"
                            type="text"
                            class="rounded-[12px] border border-[var(--border-color)] px-[12px] py-[10px] text-[14px]"
                            placeholder="https://github.com/xxx/xxx.git"
                        />
                    </div>

                    <div v-if="uploadForm.mode === 'git'" class="flex flex-col gap-[8px]">
                        <label class="font-semibold text-[var(--text-primary)]">Git 用户名</label>
                        <input
                            v-model="uploadForm.repoUsername"
                            type="text"
                            class="rounded-[12px] border border-[var(--border-color)] px-[12px] py-[10px] text-[14px]"
                            placeholder="请输入用户名"
                        />
                    </div>

                    <div v-if="uploadForm.mode === 'git'" class="flex flex-col gap-[8px]">
                        <label class="font-semibold text-[var(--text-primary)]">Git 密码</label>
                        <input
                            v-model="uploadForm.repoPassword"
                            type="password"
                            class="rounded-[12px] border border-[var(--border-color)] px-[12px] py-[10px] text-[14px]"
                            placeholder="请输入密码或 token"
                        />
                    </div>

                    <div v-if="uploadForm.error" class="notice-inline text-[13px]">
                        {{ uploadForm.error }}
                    </div>
                </div>
                <div class="flex justify-end gap-[10px] border-t border-[var(--border-color)] px-[18px] pt-[12px] pb-[16px]">
                    <button
                        class="inline-flex items-center justify-center rounded-[12px] border border-[var(--border-color)] bg-white px-[14px] py-[9px] font-bold leading-[1.1] text-[var(--text-primary)] transition-all duration-200 hover:bg-[#f7f9fc]"
                        type="button"
                        @click="closeUpload"
                    >
                        取消
                    </button>
                    <button
                        class="inline-flex items-center justify-center rounded-[12px] border border-[var(--accent-color)] bg-[var(--accent-color)] px-[14px] py-[9px] font-bold leading-[1.1] text-white transition-all duration-200 hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-70"
                        type="button"
                        :disabled="uploadForm.uploading"
                        @click="handleUpload"
                    >
                        {{ uploadForm.uploading ? '上传中…' : '上传' }}
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
