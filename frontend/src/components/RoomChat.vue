<template>
    <div class="flex h-full w-full overflow-hidden bg-[#0b1220] text-[#e7ecf4]">
        <!-- 左侧：聊天主区域 -->
        <div class="flex flex-1 flex-col min-w-0 relative border-r border-[rgba(255,255,255,0.06)]">
            <!-- 顶部 Header -->
            <header class="flex h-[64px] items-center justify-between px-6 border-b border-[rgba(255,255,255,0.06)] bg-[rgba(11,18,32,0.8)] backdrop-blur-md z-10">
                <div class="flex flex-col min-w-0">
                    <h2 class="text-lg font-bold truncate text-[#7bc8ff]">{{ currentRoom?.roomName || '聊天室' }}</h2>
                    <p class="text-xs text-[rgba(231,236,244,0.5)] truncate">{{ currentRoom?.roomDesc || '多智能体协同空间' }}</p>
                </div>
                <div class="flex items-center gap-3">
                    <button @click="showMemberDrawer = !showMemberDrawer" 
                            class="p-2 rounded-lg hover:bg-[rgba(255,255,255,0.05)] transition-all relative"
                            title="房间成员">
                        <svg viewBox="0 0 24 24" class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                            <circle cx="9" cy="7" r="4" />
                            <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                            <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                        </svg>
                        <span class="absolute top-1 right-1 w-2 h-2 bg-[#7bc8ff] rounded-full" v-if="members.length > 0"></span>
                    </button>
                </div>
            </header>

            <!-- 消息列表 -->
            <div ref="messageListRef" class="flex-1 overflow-y-auto px-4 py-6 space-y-6 scrollbar-thin scrollbar-thumb-[rgba(255,255,255,0.1)]" @scroll="handleScroll">
                <!-- 加载更多历史 -->
                <div class="flex justify-center pb-4" v-if="hasMoreHistory">
                    <button @click="loadHistory" 
                            class="text-xs text-[#7bc8ff] hover:underline bg-[rgba(123,200,255,0.05)] px-3 py-1 rounded-full transition-all"
                            :disabled="loadingHistory">
                        {{ loadingHistory ? '加载中...' : '查看历史记录' }}
                    </button>
                </div>

                <div v-for="msg in messages" :key="msg.messageId" 
                     :class="['flex w-full gap-3', msg.senderType === 'USER' ? 'flex-row-reverse' : 'flex-row']">
                    <!-- 头像 -->
                    <div class="shrink-0">
                        <div v-if="msg.senderType === 'USER'" 
                             class="w-10 h-10 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center font-bold border border-[rgba(255,255,255,0.2)]">
                            {{ msg.senderName?.charAt(0) || 'U' }}
                        </div>
                        <img v-else :src="getAgentAvatar(msg.senderId)" 
                             class="w-10 h-10 rounded-full border border-[rgba(123,200,255,0.3)] bg-[#1a2333]" 
                             alt="Agent" />
                    </div>

                    <!-- 消息内容 -->
                    <div :class="['flex flex-col max-w-[80%]', msg.senderType === 'USER' ? 'items-end' : 'items-start']">
                        <div class="flex items-center gap-2 mb-1 px-1">
                            <span class="text-xs font-semibold text-[rgba(231,236,244,0.7)]">{{ msg.senderName }}</span>
                            <span v-if="msg.senderType === 'AGENT' || msg.senderType === 'CLIENT'" class="text-[10px] bg-[#7bc8ff]/20 text-[#7bc8ff] px-1.5 rounded border border-[#7bc8ff]/30">AI</span>
                            <span class="text-[10px] text-[rgba(231,236,244,0.3)]">{{ formatTime(msg.createTime) }}</span>
                        </div>
                        <div :class="[
                            'px-4 py-3 rounded-2xl text-sm leading-relaxed shadow-sm break-words',
                            msg.senderType === 'USER' 
                                ? 'bg-indigo-600 text-white rounded-tr-none' 
                                : 'bg-[rgba(255,255,255,0.05)] border border-[rgba(255,255,255,0.08)] rounded-tl-none'
                        ]"
                        @contextmenu.prevent="openMsgContextMenu(msg, $event)">
                            {{ msg.content }}
                        </div>
                    </div>
                </div>
                
                <!-- 占位底部，用于滚动 -->
                <div ref="bottomAnchor"></div>
            </div>

            <!-- 输入框 -->
            <div class="p-4 bg-[rgba(11,18,32,0.5)] border-t border-[rgba(255,255,255,0.06)] relative">
                <div v-if="showMsgContextMenu"
                     class="fixed bg-[#0f172a] border border-[rgba(255,255,255,0.12)] rounded-xl shadow-2xl z-50 overflow-hidden"
                     :style="{ left: msgContextMenuPos.x + 'px', top: msgContextMenuPos.y + 'px' }"
                     @click.stop>
                    <button class="w-full text-left px-4 py-2 text-sm hover:bg-[rgba(255,255,255,0.05)] transition-colors"
                            @click="mentionFromContextMenu">
                        @ TA
                    </button>
                </div>

                <!-- @ 选人弹窗 -->
                <div v-if="showAtList" class="absolute bottom-full left-4 mb-2 w-56 bg-[#1e293b] border border-[#334155] rounded-lg shadow-xl z-50 max-h-48 overflow-y-auto">
                    <ul class="py-1">
                        <li v-for="agent in filteredAtMembers" :key="agent.memberId" 
                            @click="selectAtMember(agent)"
                            class="px-3 py-2 text-sm cursor-pointer hover:bg-[#334155] flex items-center gap-2 transition-colors">
                            <span class="w-6 h-6 rounded-full bg-[#1a2333] border border-[#7bc8ff]/30 flex items-center justify-center text-xs text-[#7bc8ff]">
                                {{ agent.memberName.charAt(0) }}
                            </span>
                            <span class="text-[#e7ecf4] truncate flex-1">{{ agent.memberName }}</span>
                            <span class="text-[10px] text-[rgba(231,236,244,0.4)]">{{ agent.memberType === 'CLIENT' ? '客户端' : '智能体' }}</span>
                        </li>
                        <li v-if="filteredAtMembers.length === 0" class="px-3 py-3 text-xs text-center text-[rgba(231,236,244,0.4)]">
                            未找到匹配的成员
                        </li>
                    </ul>
                </div>

                <div class="max-w-4xl mx-auto relative flex items-end gap-3 bg-[rgba(255,255,255,0.03)] border border-[rgba(255,255,255,0.1)] rounded-2xl p-2 focus-within:border-[#7bc8ff]/50 transition-all shadow-inner">
                    <textarea ref="inputRef" v-model="inputText" 
                              rows="1"
                              @input="handleInput"
                              @keydown="handleKeydown"
                              class="flex-1 bg-transparent border-none focus:ring-0 text-sm py-2 px-3 resize-none max-h-32 overflow-y-auto scrollbar-none"
                              placeholder="发条消息，和 Agent 们聊聊吧..."></textarea>
                    <button @click="sendMessage" 
                            :disabled="!inputText.trim() || !socketReady"
                            class="p-2 bg-[#7bc8ff] text-[#0f172a] rounded-xl hover:bg-[#5db8ff] disabled:opacity-30 disabled:cursor-not-allowed transition-all shadow-lg">
                        <svg viewBox="0 0 24 24" class="w-5 h-5" fill="currentColor">
                            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
                        </svg>
                    </button>
                </div>
                <div class="mt-2 text-[10px] text-center text-[rgba(231,236,244,0.3)]">
                    按 Enter 发送，智能体将有概率响应您的对话
                </div>
            </div>
        </div>

        <!-- 右侧：成员侧滑面板 -->
        <transition name="slide">
            <aside v-if="showMemberDrawer" class="w-72 bg-[#0f172a] flex flex-col h-full border-l border-[rgba(255,255,255,0.06)] shadow-2xl z-20">
                <div class="p-6 border-b border-[rgba(255,255,255,0.06)] flex items-center justify-between">
                    <h3 class="font-bold flex items-center gap-2">
                        <span>房间成员</span>
                        <span class="text-xs bg-[rgba(255,255,255,0.1)] px-2 py-0.5 rounded-full text-[rgba(231,236,244,0.6)]">{{ members.length }}</span>
                    </h3>
                    <button @click="showMemberDrawer = false" class="text-[rgba(231,236,244,0.4)] hover:text-white">×</button>
                </div>

                <div class="flex-1 overflow-y-auto p-4 space-y-6">
                    <!-- 客户端列表 -->
                    <section>
                        <div class="flex items-center justify-between mb-3 px-2">
                            <h4 class="text-xs font-bold text-[rgba(231,236,244,0.4)] uppercase tracking-wider">客户端 (Clients)</h4>
                            <button @click="openInvite('client')" class="text-[#7bc8ff] hover:text-[#5db8ff] text-xs font-medium">+ 邀请</button>
                        </div>
                        <div class="space-y-2">
                            <div v-for="client in clientMembers" :key="client.memberId" class="group flex items-center justify-between p-2 rounded-xl hover:bg-[rgba(255,255,255,0.03)] transition-all">
                                <div class="flex items-center gap-3 min-w-0">
                                    <img :src="getAgentAvatar(client.memberId)" class="w-8 h-8 rounded-lg border border-[rgba(123,200,255,0.2)]" alt="" />
                                    <span class="text-sm font-medium truncate">{{ client.memberName }}</span>
                                </div>
                                <button @click="removeMember(client.memberId)" class="opacity-0 group-hover:opacity-100 p-1 text-[#ef4444] hover:bg-[#ef4444]/10 rounded transition-all">🗑</button>
                            </div>
                            <div v-if="clientMembers.length === 0" class="text-center py-4 text-xs text-[rgba(231,236,244,0.3)] italic">暂无客户端</div>
                        </div>
                    </section>

                    <section>
                        <div class="flex items-center justify-between mb-3 px-2">
                            <h4 class="text-xs font-bold text-[rgba(231,236,244,0.4)] uppercase tracking-wider">智能体 (Agents)</h4>
                            <button @click="openInvite('agent')" class="text-[#7bc8ff] hover:text-[#5db8ff] text-xs font-medium">+ 邀请</button>
                        </div>
                        <div class="space-y-2">
                            <div v-for="agent in agentMembers" :key="agent.memberId" class="group flex items-center justify-between p-2 rounded-xl hover:bg-[rgba(255,255,255,0.03)] transition-all">
                                <div class="flex items-center gap-3 min-w-0">
                                    <img :src="getAgentAvatar(agent.memberId)" class="w-8 h-8 rounded-lg border border-[rgba(123,200,255,0.2)]" alt="" />
                                    <span class="text-sm font-medium truncate">{{ agent.memberName }}</span>
                                </div>
                                <button @click="removeMember(agent.memberId)" class="opacity-0 group-hover:opacity-100 p-1 text-[#ef4444] hover:bg-[#ef4444]/10 rounded transition-all">🗑</button>
                            </div>
                            <div v-if="agentMembers.length === 0" class="text-center py-4 text-xs text-[rgba(231,236,244,0.3)] italic">暂无智能体</div>
                        </div>
                    </section>

                    <!-- 人类用户列表 -->
                    <section>
                        <h4 class="text-xs font-bold text-[rgba(231,236,244,0.4)] uppercase tracking-wider mb-3 px-2">在线用户</h4>
                        <div class="space-y-2">
                            <div v-for="human in humanMembers" :key="human.memberId" class="flex items-center gap-3 p-2">
                                <div class="w-8 h-8 rounded-lg bg-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold text-xs border border-indigo-500/30">
                                    {{ human.memberName?.charAt(0) }}
                                </div>
                                <span class="text-sm font-medium">{{ human.memberName }}</span>
                                <span v-if="human.memberId === currentUser?.username || human.memberId === currentUser?.userName" class="text-[10px] text-[rgba(231,236,244,0.3)]">(你)</span>
                            </div>
                        </div>
                    </section>
                </div>
            </aside>
        </transition>

        <!-- 邀请弹窗 -->
        <div v-if="showInviteModal" class="fixed inset-0 z-50 grid place-items-center bg-[rgba(0,0,0,0.6)] backdrop-blur-md p-4" @click.self="showInviteModal = false">
            <div class="w-full max-w-lg bg-[#0f172a] rounded-3xl border border-[rgba(255,255,255,0.1)] shadow-2xl overflow-hidden flex flex-col max-h-[80vh]">
                <div class="p-6 border-b border-[rgba(255,255,255,0.06)] flex items-center justify-between">
                    <div>
                        <h3 class="text-xl font-bold text-[#7bc8ff]">邀请成员</h3>
                        <p class="text-xs text-[rgba(231,236,244,0.5)] mt-1">{{ inviteMode === 'client' ? '选择一个 Client 加入群聊' : '选择一个 Agent 加入群聊' }}</p>
                    </div>
                    <button @click="showInviteModal = false" class="w-8 h-8 grid place-items-center rounded-full hover:bg-[rgba(255,255,255,0.05)]">×</button>
                </div>
                
                <div class="flex-1 overflow-y-auto p-4 grid grid-cols-2 gap-3 scrollbar-thin">
                    <div v-for="item in inviteList" :key="inviteMode === 'client' ? item.clientId : item.agentId" 
                         @click="inviteMode === 'client' ? addClient(item) : addAgent(item)"
                         class="p-4 rounded-2xl border border-[rgba(255,255,255,0.06)] bg-[rgba(255,255,255,0.02)] hover:border-[#7bc8ff] hover:bg-[#7bc8ff]/5 cursor-pointer transition-all group">
                        <div class="flex flex-col items-center text-center gap-3">
                            <img :src="getAgentAvatar(inviteMode === 'client' ? item.clientId : item.agentId)" class="w-16 h-16 rounded-2xl border border-[rgba(255,255,255,0.1)] group-hover:scale-105 transition-transform" />
                            <div>
                                <div class="font-bold text-sm">{{ inviteMode === 'client' ? (item.clientName || item.clientId) : (item.agentName || item.agentId) }}</div>
                                <div class="text-[10px] text-[rgba(231,236,244,0.4)] mt-1 line-clamp-1">{{ inviteMode === 'client' ? (item.clientRole || 'CLIENT') : (item.agentDesc || 'AGENT') }}</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick, reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { 
    useRoomStore, 
    useAuthStore, 
    useAgentStore 
} from '../router/pinia';
import { 
    chatRoomMemberList, 
    chatRoomMemberAdd, 
    chatRoomMemberRemove, 
    chatRoomMessageCursor,
    queryChatModels as listClients,
    queryAgentList as listAgents
} from '../request/api';
import toast from '../utils/toast';
import { formatTime } from '../utils/DatetimeUtil';

const route = useRoute();
const router = useRouter();
const roomStore = useRoomStore();
const authStore = useAuthStore();
const agentStore = useAgentStore();

const roomId = computed(() => route.params.roomId);
const currentRoom = computed(() => roomStore.currentRoom);
const messages = computed(() => roomStore.messages);
const members = computed(() => roomStore.members);
const currentUser = computed(() => authStore.user);

const agentMembers = computed(() => members.value.filter(m => m.memberType === 'AGENT'));
const clientMembers = computed(() => members.value.filter(m => m.memberType === 'CLIENT'));
const humanMembers = computed(() => members.value.filter(m => m.memberType === 'USER'));

const inputText = ref('');
const showMemberDrawer = ref(false);
const showInviteModal = ref(false);
const loadingHistory = ref(false);
const hasMoreHistory = ref(true);
const socketReady = ref(false);
const availableClients = ref([]);
const availableAgents = ref([]);
const inviteMode = ref('client');

const inviteList = computed(() => (inviteMode.value === 'client' ? availableClients.value : availableAgents.value));

const messageListRef = ref(null);
const bottomAnchor = ref(null);
const inputRef = ref(null);

// -------------------- @ 选人逻辑状态 --------------------
const showAtList = ref(false);
const atSearchText = ref('');
const selectedAtMembers = ref([]);

const filteredAtMembers = computed(() => {
    // 可以被 @ 的成员：类型为 CLIENT 或 AGENT
    const bots = members.value.filter(m => m.memberType === 'CLIENT' || m.memberType === 'AGENT');
    if (!atSearchText.value) return bots;
    return bots.filter(m => m.memberName.toLowerCase().includes(atSearchText.value.toLowerCase()));
});

const handleInput = (e) => {
    const text = inputText.value;
    const cursorPosition = e.target.selectionStart;
    
    // 找光标前最后一个 @
    const lastAtIdx = text.lastIndexOf('@', cursorPosition - 1);
    if (lastAtIdx !== -1) {
        // 提取 @ 之后到光标位置的文本作为搜索词
        const searchStr = text.slice(lastAtIdx + 1, cursorPosition);
        // 如果中间没有空格，说明正在 @
        if (!searchStr.includes(' ')) {
            atSearchText.value = searchStr;
            showAtList.value = true;
        } else {
            showAtList.value = false;
        }
    } else {
        showAtList.value = false;
    }
};

const handleKeydown = (e) => {
    // 拦截 Enter 发送
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
        return;
    }

    // 拦截 Backspace 整体删除 @ 标签
    if (e.key === 'Backspace') {
        const text = inputText.value;
        const cursorPosition = inputRef.value.selectionStart;
        
        // 检查光标前面是否是一个已选的 @标签
        for (const member of selectedAtMembers.value) {
            const tagText = `@${member.name} `;
            // 找当前标签在光标前的最近一次出现位置
            const tagIndex = text.lastIndexOf(tagText, cursorPosition);
            
            // 如果光标刚好在这个标签内部或者紧贴着标签末尾
            if (tagIndex !== -1 && cursorPosition > tagIndex && cursorPosition <= tagIndex + tagText.length) {
                e.preventDefault(); // 阻止默认删除行为
                
                // 将整个标签从文本中删除
                const newText = text.slice(0, tagIndex) + text.slice(tagIndex + tagText.length);
                inputText.value = newText;
                
                // 从已选列表中移除
                selectedAtMembers.value = selectedAtMembers.value.filter(m => m.id !== member.id);
                
                // 恢复光标位置
                nextTick(() => {
                    inputRef.value.focus();
                    inputRef.value.setSelectionRange(tagIndex, tagIndex);
                });
                return;
            }
        }
    }
};

const selectAtMember = (agent) => {
    const text = inputText.value;
    const cursorPosition = inputRef.value.selectionStart;
    const lastAtIdx = text.lastIndexOf('@', cursorPosition - 1);
    
    if (lastAtIdx !== -1) {
        // 替换 @ 后面的文本为选中的成员名称，并加一个空格
        const beforeAt = text.slice(0, lastAtIdx);
        const afterCursor = text.slice(cursorPosition);
        const replaceText = `@${agent.memberName} `;
        
        inputText.value = beforeAt + replaceText + afterCursor;
        
        // 存入已选列表 (去重)
        if (!selectedAtMembers.value.find(m => m.id === agent.memberId)) {
            selectedAtMembers.value.push({ id: agent.memberId, name: agent.memberName });
        }
        
        // 将焦点重新定位到文本框
        nextTick(() => {
            inputRef.value.focus();
            const newCursorPos = beforeAt.length + replaceText.length;
            inputRef.value.setSelectionRange(newCursorPos, newCursorPos);
        });
    }
    
    showAtList.value = false;
    atSearchText.value = '';
};

const showMsgContextMenu = ref(false);
const msgContextMenuPos = reactive({ x: 0, y: 0 });
const msgContextMenuMember = ref(null);

const openMsgContextMenu = (msg, event) => {
    if (msg.senderType !== 'CLIENT' && msg.senderType !== 'AGENT') {
        showMsgContextMenu.value = false;
        return;
    }

    msgContextMenuMember.value = {
        memberId: msg.senderId,
        memberName: msg.senderName,
        memberType: msg.senderType
    };

    const menuWidth = 140;
    const menuHeight = 44;
    const padding = 8;

    const maxX = Math.max(padding, window.innerWidth - menuWidth - padding);
    const maxY = Math.max(padding, window.innerHeight - menuHeight - padding);

    msgContextMenuPos.x = Math.min(Math.max(event.clientX, padding), maxX);
    msgContextMenuPos.y = Math.min(Math.max(event.clientY, padding), maxY);

    showMsgContextMenu.value = true;
    showAtList.value = false;
};

const closeMsgContextMenu = () => {
    showMsgContextMenu.value = false;
    msgContextMenuMember.value = null;
};

const insertTextAtCursor = (textToInsert) => {
    const el = inputRef.value;
    const text = inputText.value;
    const start = el?.selectionStart ?? text.length;
    const end = el?.selectionEnd ?? start;

    inputText.value = text.slice(0, start) + textToInsert + text.slice(end);

    nextTick(() => {
        inputRef.value?.focus();
        const newPos = start + textToInsert.length;
        inputRef.value?.setSelectionRange(newPos, newPos);
    });
};

const mentionMember = (memberId, memberName) => {
    if (!memberId || !memberName) return;

    if (!selectedAtMembers.value.find(m => m.id === memberId)) {
        selectedAtMembers.value.push({ id: memberId, name: memberName });
    }

    const tagText = `@${memberName} `;
    insertTextAtCursor(tagText);
};

const mentionFromContextMenu = () => {
    const member = msgContextMenuMember.value;
    if (member) {
        mentionMember(member.memberId, member.memberName);
    }
    closeMsgContextMenu();
};

let socket = null;

// -------------------- WebSocket 逻辑 --------------------
const initWebSocket = () => {
    if (socket) {
        socket.close();
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    
    // 调试日志：看清当前用户对象的真面目
    console.log('【WebSocket Debug】当前用户对象:', currentUser.value);
    console.log('【WebSocket Debug】可用字段:', currentUser.value ? Object.keys(currentUser.value) : 'null');

    // 尝试所有可能的字段
    const username = currentUser.value?.username || currentUser.value?.userName;
    
    if (!username) {
        console.error('【WebSocket】无法获取当前用户名，请检查登录状态');
        return;
    }
    // 补全完整的 context-path 路径：/miniagent/api/v1
    const wsUrl = `ws://localhost:8066/miniagent/api/v1/ws/room/${roomId.value}/${username}`;
    console.log('【WebSocket Debug】准备连接 URL:', wsUrl);

    socket = new WebSocket(wsUrl);

    socket.onopen = () => {
        console.log('【WebSocket】连接成功');
        socketReady.value = true;
    };

    socket.onmessage = (event) => {
        try {
            const wsEvent = JSON.parse(event.data);
            handleIncomingEvent(wsEvent);
        } catch (e) {
            console.error('【WebSocket】解析消息失败', e);
        }
    };

    socket.onclose = () => {
        console.log('【WebSocket】连接关闭');
        socketReady.value = false;
    };

    socket.onerror = (err) => {
        console.error('【WebSocket】发生错误', err);
    };
};

const handleIncomingEvent = (wsEvent) => {
    const { eventType, payload } = wsEvent;
    
    if (eventType === 'USER_MSG' || eventType === 'AGENT_MSG' || eventType === 'CLIENT_MSG') {
        roomStore.pushMessage(payload);
        scrollToBottom();
    }
};

const sendMessage = () => {
    if (!inputText.value.trim() || !socketReady.value) return;

    // 组装逗号分隔的 id 字符串
    const atMemberIdsStr = selectedAtMembers.value.map(m => m.id).join(',');

    const payload = {
        content: inputText.value.trim(),
        traceId: 'trace_' + Date.now(),
        atMemberIds: atMemberIdsStr
    };

    socket.send(JSON.stringify(payload));
    
    // 清空输入框和已选成员
    inputText.value = '';
    selectedAtMembers.value = [];
    showAtList.value = false;
    
    nextTick(() => scrollToBottom());
};

// -------------------- 业务数据逻辑 --------------------
const loadMembers = async () => {
    try {
        const res = await chatRoomMemberList(roomId.value);
        if (res.code === 200) {
            roomStore.setMembers(res.data);
        }
    } catch (e) {
        console.error('加载成员失败', e);
    }
};

const loadHistory = async (isInitial = false) => {
    if (loadingHistory.value) return;
    loadingHistory.value = true;
    
    try {
        // 使用第一个消息的时间戳作为游标
        const cursorTime = isInitial ? null : (messages.value[0]?.timestamp || null);
        const res = await chatRoomMessageCursor({
            roomId: roomId.value,
            cursorTime: cursorTime,
            pageSize: 20
        });

        if (res.code === 200) {
            const newMsgs = res.data || [];
            if (newMsgs.length < 20) {
                hasMoreHistory.value = false;
            }
            
            if (isInitial) {
                // 初始化时直接设置
                roomStore.setMessages(newMsgs.reverse());
                nextTick(() => scrollToBottom('auto'));
            } else if (newMsgs.length > 0) {
                // 向上加载时 prepend
                roomStore.prependMessages(newMsgs.reverse());
            }
        }
    } catch (e) {
        console.error('加载历史记录失败', e);
    } finally {
        loadingHistory.value = false;
    }
};

const loadAvailableClients = async () => {
    try {
        const res = await listClients();
        if (res.code === 200) {
            // queryChatModels 返回的是一个列表，每个对象包含 clientId, clientName 等
            availableClients.value = res.data || [];
        }
    } catch (e) {
        console.error('加载可用 Client 失败', e);
    }
};

const loadAvailableAgents = async () => {
    try {
        const res = await listAgents();
        if (res.code === 200) {
            availableAgents.value = res.data || [];
        }
    } catch (e) {
        console.error('加载可用 Agent 失败', e);
    }
};

const openInvite = (mode) => {
    inviteMode.value = mode === 'agent' ? 'agent' : 'client';
    showInviteModal.value = true;
};

const addClient = async (client) => {
    try {
        const res = await chatRoomMemberAdd({
            roomId: roomId.value,
            memberId: client.clientId,
            memberType: 'CLIENT'
        });
        if (res.code === 200) {
            toast.show(`已邀请 ${client.clientName || client.clientId} 加入房间`);
            showInviteModal.value = false;
            loadMembers();
        }
    } catch (e) {
        toast.show('邀请失败');
    }
};

const addAgent = async (agent) => {
    try {
        const res = await chatRoomMemberAdd({
            roomId: roomId.value,
            memberId: agent.agentId,
            memberType: 'AGENT'
        });
        if (res.code === 200) {
            toast.show(`已邀请 ${agent.agentName || agent.agentId} 加入房间`);
            showInviteModal.value = false;
            loadMembers();
        }
    } catch (e) {
        toast.show('邀请失败');
    }
};

const removeMember = async (memberId) => {
    try {
        const res = await chatRoomMemberRemove({
            roomId: roomId.value,
            memberId: memberId
        });
        if (res.code === 200) {
            toast.show('已移除成员');
            loadMembers();
        }
    } catch (e) {
        toast.show('移除失败');
    }
};

// -------------------- UI 辅助 --------------------
const scrollToBottom = (behavior = 'smooth') => {
    nextTick(() => {
        bottomAnchor.value?.scrollIntoView({ behavior });
    });
};

const handleScroll = (e) => {
    const el = e.target;
    // 距离顶部 20px 时加载历史
    if (el.scrollTop < 20 && hasMoreHistory.value && !loadingHistory.value) {
        // 记录当前位置
        const oldHeight = el.scrollHeight;
        loadHistory().then(() => {
            nextTick(() => {
                // 恢复滚动位置
                const newHeight = el.scrollHeight;
                el.scrollTop = newHeight - oldHeight;
            });
        });
    }
};

const getAgentAvatar = (clientId) => {
    const styles = ['bottts', 'identicon', 'avataaars', 'pixel-art', 'big-smile'];
    // 简单的哈希逻辑，根据 clientId 选择风格
    let hash = 0;
    if (clientId) {
        for (let i = 0; i < clientId.length; i++) {
            hash = clientId.charCodeAt(i) + ((hash << 5) - hash);
        }
    }
    const index = Math.abs(hash) % styles.length;
    const style = styles[index];
    return `https://api.dicebear.com/7.x/${style}/svg?seed=${clientId}&backgroundColor=b6e3f4,c0aede,d1d4f9`;
};

// -------------------- 生命周期 --------------------
onMounted(() => {
    roomStore.setCurrentRoomId(roomId.value);
    loadMembers();
    loadHistory(true);
    initWebSocket();
    loadAvailableClients();
    loadAvailableAgents();
    window.addEventListener('click', closeMsgContextMenu);
});

onBeforeUnmount(() => {
    if (socket) {
        socket.close();
    }
    window.removeEventListener('click', closeMsgContextMenu);
});

watch(roomId, (newId) => {
    if (newId) {
        roomStore.setCurrentRoomId(newId);
        hasMoreHistory.value = true;
        loadMembers();
        loadHistory(true);
        initWebSocket();
    }
});
</script>

<style scoped>
.slide-enter-active, .slide-leave-active {
    transition: transform 0.3s ease;
}
.slide-enter-from, .slide-leave-to {
    transform: translateX(100%);
}

/* 隐藏滚动条但保留功能 */
.scrollbar-none::-webkit-scrollbar {
    display: none;
}
.scrollbar-none {
    -ms-overflow-style: none;
    scrollbar-width: none;
}
</style>
