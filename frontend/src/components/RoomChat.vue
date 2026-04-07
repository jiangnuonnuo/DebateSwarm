<template>
    <div class="flex h-full w-full overflow-hidden bg-[radial-gradient(circle_at_top_right,rgba(59,130,246,0.12),transparent_30%),linear-gradient(180deg,#0f172a_0%,#020617_100%)] text-[#f1f5f9]">
        <!-- 左侧：聊天主区域 -->
        <div class="flex flex-1 flex-col min-w-0 relative border-r border-white/5">
            <!-- 顶部 Header -->
            <header class="flex h-[64px] items-center justify-between px-6 border-b border-white/5 bg-slate-900/50 backdrop-blur-xl z-10">
                <div class="flex flex-col min-w-0">
                    <h2 class="text-base font-bold truncate text-blue-400">{{ currentRoom?.roomName || '聊天室' }}</h2>
                    <p class="text-[11px] text-slate-500 truncate">{{ currentRoom?.roomDesc || '多智能体协同空间' }}</p>
                </div>
                <div class="flex items-center gap-3">
                    <button @click="showDebatePanel = !showDebatePanel"
                            class="px-3 py-1.5 rounded-lg border border-blue-500/20 text-[11px] font-bold text-blue-400 hover:bg-blue-500/10 transition-all"
                            :class="showDebatePanel ? 'bg-blue-500/10' : ''"
                            title="辩论面板">
                        {{ showDebatePanel ? '收起面板' : '辩论设置' }}
                    </button>
                    <button @click="showMemberDrawer = !showMemberDrawer" 
                            class="p-2 rounded-lg hover:bg-white/5 transition-all relative"
                            title="房间成员">
                        <svg viewBox="0 0 24 24" class="w-5 h-5 opacity-60" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                            <circle cx="9" cy="7" r="4" />
                        </svg>
                        <span class="absolute top-1.5 right-1.5 w-1.5 h-1.5 bg-blue-500 rounded-full" v-if="members.length > 0"></span>
                    </button>
                </div>
            </header>

            <section v-if="debatePanelVisible"
                     class="px-4 py-4 border-b border-white/5 bg-slate-900/80 backdrop-blur-md">
                <div class="grid gap-4 lg:grid-cols-[1.2fr,0.8fr]">
                    <div class="rounded-2xl border border-white/5 bg-black/20 p-4 space-y-4">
                        <div class="flex items-start justify-between gap-4">
                            <div>
                                <p class="text-[10px] uppercase tracking-widest text-slate-500 font-bold">DEBATE STATUS</p>
                                <h3 class="mt-1 text-base font-bold text-slate-200">
                                    {{ debateStatus?.topic || debateTopic || '尚未开始辩论' }}
                                </h3>
                            </div>
                            <div class="flex flex-col items-end gap-2 text-right">
                                <span class="px-2 py-0.5 rounded-full text-[10px] font-bold border border-blue-500/30 text-blue-400 bg-blue-500/5">
                                    {{ debateStatusLabel }}
                                </span>
                            </div>
                        </div>

                        <div class="grid gap-3 sm:grid-cols-2">
                            <div class="rounded-xl border border-white/5 bg-white/5 p-3">
                                <p class="text-[10px] font-bold text-slate-500">当前轮次</p>
                                <p class="mt-1 text-sm font-bold text-blue-300">
                                    Round {{ debateStatus?.currentRound || debateStatus?.pendingRoundNumber || 0 }}
                                </p>
                            </div>
                            <div class="rounded-xl border border-white/5 bg-white/5 p-3">
                                <p class="text-[10px] font-bold text-slate-500">发言进度</p>
                                <p class="mt-1 text-sm font-bold text-blue-300">
                                    {{ debateStatus?.currentTurn || 0 }}/{{ debateStatus?.turnsPerRound || debateTurns || 0 }}
                                </p>
                            </div>
                        </div>

                        <div class="grid gap-3 md:grid-cols-2">
                            <div class="rounded-xl border border-blue-500/20 bg-blue-500/10 p-3">
                                <div class="flex items-center justify-between">
                                    <span class="text-[11px] font-bold text-blue-400">正方</span>
                                    <span class="text-[10px] text-blue-400/50">{{ (debateStatus?.proMembers?.length || selectedProIds.length) }} 人</span>
                                </div>
                                <div class="mt-2 flex flex-wrap gap-2">
                                    <span v-for="member in (debateStatus?.proMembers?.length ? debateStatus.proMembers : selectedProMembers)"
                                          :key="member.clientId"
                                          class="px-2 py-0.5 rounded-lg text-[11px] bg-blue-500/10 border border-blue-500/30 text-blue-200">
                                        {{ member.clientName }}
                                    </span>
                                    <span v-if="!(debateStatus?.proMembers?.length || selectedProMembers.length)"
                                          class="text-[10px] text-slate-500">暂未选择</span>
                                </div>
                            </div>
                            <div class="rounded-xl border border-slate-500/20 bg-slate-500/10 p-3">
                                <div class="flex items-center justify-between">
                                    <span class="text-[11px] font-bold text-slate-400">反方</span>
                                    <span class="text-[10px] text-slate-500/50">{{ (debateStatus?.conMembers?.length || selectedConIds.length) }} 人</span>
                                </div>
                                <div class="mt-2 flex flex-wrap gap-2">
                                    <span v-for="member in (debateStatus?.conMembers?.length ? debateStatus.conMembers : selectedConMembers)"
                                          :key="member.clientId"
                                          class="px-2 py-0.5 rounded-lg text-[11px] bg-slate-500/10 border border-slate-500/30 text-slate-300">
                                        {{ member.clientName }}
                                    </span>
                                    <span v-if="!(debateStatus?.conMembers?.length || selectedConMembers.length)"
                                          class="text-[10px] text-slate-500">暂未选择</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="rounded-2xl border border-white/5 bg-black/20 p-4 space-y-4">
                        <div class="space-y-2">
                            <label class="text-[10px] font-bold text-slate-500 uppercase tracking-wider">仲裁者</label>
                            <div class="flex gap-2">
                                <select v-model="selectedArbitratorId"
                                        class="flex-1 rounded-xl border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-slate-200 outline-none focus:border-blue-500/40">
                                    <option value="">选择仲裁模型</option>
                                    <option v-for="option in debateClientOptions" :key="option.id" :value="option.id">
                                        {{ option.name }}
                                    </option>
                                </select>
                                <button @click="setDebateArbitrator"
                                        :disabled="debateLoading || !selectedArbitratorId"
                                        class="px-3 py-1.5 rounded-xl bg-blue-600 text-white text-[11px] font-bold disabled:opacity-30 transition-all hover:bg-blue-500">
                                    设置
                                </button>
                                <button @click="removeDebateArbitrator"
                                        :disabled="debateLoading || !debateStatus?.arbitratorClientId"
                                        class="px-3 py-1.5 rounded-xl border border-white/10 text-[11px] font-bold text-white/80 disabled:opacity-30 transition-all hover:bg-white/10">
                                    移除
                                </button>
                            </div>
                            <p class="text-[10px] text-slate-500" v-if="!debateClientOptions.length">
                                当前房间暂无 CLIENT 成员，请先邀请客户端加入房间。
                            </p>
                        </div>

                        <div class="grid gap-3 md:grid-cols-2">
                            <div class="rounded-xl border border-blue-500/20 bg-blue-500/5 p-3 space-y-2">
                                <div class="flex items-center justify-between">
                                    <span class="text-[11px] font-bold text-blue-400">选择正方</span>
                                    <span class="text-[10px] text-blue-400/50">{{ selectedProIds.length }} 人</span>
                                </div>
                                <div class="max-h-28 overflow-y-auto space-y-1 pr-1">
                                    <label v-for="option in availableDebaterOptions" :key="`pro-${option.id}`"
                                           class="flex items-center gap-2 rounded-lg px-2 py-1 text-[11px] hover:bg-blue-500/10 cursor-pointer">
                                        <input type="checkbox"
                                               class="accent-blue-500"
                                               :checked="selectedProIds.includes(option.id)"
                                               @change="toggleDebater('PRO', option.id)">
                                        <span class="truncate">{{ option.name }}</span>
                                    </label>
                                    <p v-if="!availableDebaterOptions.length" class="text-[10px] text-slate-500 px-2 py-1">
                                        无可选辩手
                                    </p>
                                </div>
                            </div>
                            <div class="rounded-xl border border-slate-500/20 bg-slate-500/5 p-3 space-y-2">
                                <div class="flex items-center justify-between">
                                    <span class="text-[11px] font-bold text-slate-300">选择反方</span>
                                    <span class="text-[10px] text-slate-500/70">{{ selectedConIds.length }} 人</span>
                                </div>
                                <div class="max-h-28 overflow-y-auto space-y-1 pr-1">
                                    <label v-for="option in availableDebaterOptions" :key="`con-${option.id}`"
                                           class="flex items-center gap-2 rounded-lg px-2 py-1 text-[11px] hover:bg-white/5 cursor-pointer">
                                        <input type="checkbox"
                                               class="accent-slate-400"
                                               :checked="selectedConIds.includes(option.id)"
                                               @change="toggleDebater('CON', option.id)">
                                        <span class="truncate">{{ option.name }}</span>
                                    </label>
                                    <p v-if="!availableDebaterOptions.length" class="text-[10px] text-slate-500 px-2 py-1">
                                        无可选辩手
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div class="grid gap-3 sm:grid-cols-[1fr,80px]">
                            <label class="space-y-2">
                                <span class="text-[10px] font-bold text-slate-500 uppercase tracking-wider">辩题</span>
                                <input v-model="debateTopic"
                                       type="text"
                                       class="w-full rounded-xl border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-slate-200 outline-none focus:border-blue-500/40"
                                       placeholder="输入辩论主题..." />
                            </label>
                            <label class="space-y-2">
                                <span class="text-[10px] font-bold text-slate-500 uppercase tracking-wider">发言数</span>
                                <input v-model="debateTurns"
                                       type="number"
                                       class="w-full rounded-xl border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-slate-200 outline-none focus:border-blue-500/40" />
                            </label>
                        </div>

                        <div class="flex flex-wrap gap-2">
                            <button @click="startDebateFlow"
                                    :disabled="debateLoading || isDebateRunning"
                                    class="px-4 py-2 rounded-xl bg-blue-600 text-white text-[11px] font-bold disabled:opacity-30 transition-all hover:bg-blue-500">
                                开始辩论
                            </button>
                            <button v-if="canDeclareWinner"
                                    @click="showWinnerModal = true"
                                    class="px-4 py-2 rounded-xl bg-blue-400 text-blue-950 text-[11px] font-bold transition-all hover:brightness-110">
                                裁决胜方
                            </button>
                            <button v-if="canStartNextRound"
                                    @click="startDebateNextRound"
                                    class="px-4 py-2 rounded-xl bg-emerald-500 text-emerald-950 text-[11px] font-bold hover:bg-emerald-400 transition-all">
                                开始下一轮
                            </button>
                            <button v-if="canStopDebate"
                                    @click="stopDebateFlow"
                                    class="px-4 py-2 rounded-xl border border-blue-500/30 text-blue-400 text-[11px] font-bold hover:bg-blue-500/10 transition-all">
                                结束
                            </button>
                            <button @click="openInvite('client')"
                                    class="px-4 py-2 rounded-xl border border-white/10 text-white/70 text-[11px] font-bold hover:bg-white/10 transition-all">
                                邀请客户端
                            </button>
                        </div>
                        <p v-if="canStartNextRound" class="text-[10px] text-emerald-300/80">
                            当前处于轮间阶段：支持 @ 指定客户端回复；未 @ 消息不会触发自动回复。
                        </p>
                    </div>
                </div>
            </section>

            <!-- 消息列表 -->
            <div ref="messageListRef" class="flex-1 overflow-y-auto px-4 py-6 space-y-6 scrollbar-thin scrollbar-thumb-white/10" @scroll="handleScroll">
                <!-- 加载更多历史 -->
                <div class="flex justify-center pb-4" v-if="hasMoreHistory">
                    <button @click="loadHistory" 
                            class="text-xs text-blue-400 hover:underline bg-blue-500/5 px-3 py-1 rounded-full transition-all"
                            :disabled="loadingHistory">
                        {{ loadingHistory ? '加载中...' : '查看历史记录' }}
                    </button>
                </div>

                <div v-for="msg in messages" :key="msg.messageId">
                    <div v-if="msg.senderType === 'SYSTEM'" class="flex justify-center">
                        <div :class="systemNoticeCardClass(msg)">
                            <div class="flex items-center justify-between gap-3">
                                <div class="text-[10px] uppercase tracking-[0.18em]" :class="systemNoticeLabelClass(msg)">{{ parseSystemNoticeType(msg) }}</div>
                                <div class="text-[10px] text-white/20">{{ formatTime(msg.createTime) }}</div>
                            </div>
                            <div class="mt-1 text-xs leading-6" :class="systemNoticeContentClass(msg)">{{ msg.content }}</div>
                        </div>
                    </div>
                    <div v-else :class="['flex w-full gap-3', msg.senderType === 'USER' ? 'flex-row-reverse' : 'flex-row']">
                        <!-- 头像 -->
                        <div class="shrink-0">
                            <div v-if="msg.senderType === 'USER'" 
                                 class="w-10 h-10 rounded-full bg-gradient-to-br from-blue-500 to-blue-700 flex items-center justify-center font-bold border border-white/10 shadow-lg">
                                {{ msg.senderName?.charAt(0) || 'U' }}
                            </div>
                            <img v-else :src="getAgentAvatar(msg.senderId)" 
                                 class="w-10 h-10 rounded-full border border-blue-500/20 bg-slate-800" 
                                 alt="Agent" />
                        </div>

                        <!-- 消息内容 -->
                        <div :class="['flex flex-col max-w-[80%]', msg.senderType === 'USER' ? 'items-end' : 'items-start']">
                            <div class="flex items-center gap-2 mb-1 px-1">
                                <span class="text-xs font-semibold text-white/60">{{ msg.senderName }}</span>
                                <span v-if="msg.senderType === 'AGENT' || msg.senderType === 'CLIENT'" class="text-[10px] bg-blue-500/10 text-blue-400 px-1.5 rounded border border-blue-500/20">AI</span>
                                <span class="text-[10px] text-white/20">{{ formatTime(msg.createTime) }}</span>
                            </div>
                            <div :class="[
                                'px-4 py-3 rounded-2xl text-sm leading-relaxed shadow-sm break-words',
                                msg.senderType === 'USER' 
                                    ? 'bg-blue-600 text-white rounded-tr-none shadow-blue-500/10' 
                                    : 'bg-white/5 border border-white/10 rounded-tl-none'
                            ]"
                            @contextmenu.prevent="openMsgContextMenu(msg, $event)">
                                {{ msg.content }}
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- 占位底部，用于滚动 -->
                <div ref="bottomAnchor"></div>
            </div>

            <!-- 输入框 -->
            <div class="p-4 bg-slate-900/40 border-t border-white/5 backdrop-blur-md relative">
                <div v-if="showMsgContextMenu"
                     class="fixed bg-slate-900 border border-white/10 rounded-xl shadow-2xl z-50 overflow-hidden"
                     :style="{ left: msgContextMenuPos.x + 'px', top: msgContextMenuPos.y + 'px' }"
                     @click.stop>
                    <button class="w-full text-left px-4 py-2 text-sm hover:bg-white/5 transition-colors"
                            @click="mentionFromContextMenu">
                        @ TA
                    </button>
                </div>

                <!-- @ 选人弹窗 -->
                <div v-if="showAtList" class="absolute bottom-full left-4 mb-2 w-56 bg-slate-800 border border-white/10 rounded-lg shadow-xl z-50 max-h-48 overflow-y-auto">
                    <ul class="py-1">
                        <li v-for="agent in filteredAtMembers" :key="agent.memberId" 
                            @click="selectAtMember(agent)"
                            class="px-3 py-2 text-sm cursor-pointer hover:bg-white/5 flex items-center gap-2 transition-colors">
                            <span class="w-6 h-6 rounded-full bg-slate-900 border border-blue-500/30 flex items-center justify-center text-xs text-blue-400">
                                {{ agent.memberName.charAt(0) }}
                            </span>
                            <span class="text-white truncate flex-1">{{ agent.memberName }}</span>
                            <span class="text-[10px] text-white/30">{{ agent.memberType === 'CLIENT' ? '客户端' : '智能体' }}</span>
                        </li>
                    </ul>
                </div>

                <div class="max-w-4xl mx-auto relative flex items-end gap-3 bg-white/5 border border-white/10 rounded-[22px] p-2 focus-within:border-blue-500/40 transition-all shadow-xl">
                    <textarea ref="inputRef" v-model="inputText" 
                              rows="1"
                              @input="handleInput"
                              @keydown="handleKeydown"
                              class="flex-1 bg-transparent border-none focus:ring-0 text-sm py-2 px-3 resize-none max-h-32 overflow-y-auto scrollbar-none text-white"
                              placeholder="发条消息，和 Agent 们聊聊吧..."></textarea>
                    <button @click="sendMessage" 
                            :disabled="!inputText.trim() || !socketReady"
                            class="p-2 bg-blue-500 text-slate-900 rounded-xl hover:bg-blue-400 disabled:opacity-30 disabled:cursor-not-allowed transition-all shadow-lg">
                        <svg viewBox="0 0 24 24" class="w-5 h-5" fill="currentColor">
                            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" />
                        </svg>
                    </button>
                </div>
            </div>
        </div>

        <!-- 右侧：成员侧滑面板 -->
        <transition name="slide">
            <aside v-if="showMemberDrawer" class="w-72 bg-slate-900 flex flex-col h-full border-l border-white/5 shadow-2xl z-20">
                <div class="p-6 border-b border-white/5 flex items-center justify-between">
                    <h3 class="font-bold flex items-center gap-2">
                        <span>房间成员</span>
                        <span class="text-xs bg-white/10 px-2 py-0.5 rounded-full text-white/60">{{ members.length }}</span>
                    </h3>
                    <button @click="showMemberDrawer = false" class="text-white/40 hover:text-white">×</button>
                </div>

                <div class="flex-1 overflow-y-auto p-4 space-y-6">
                    <section>
                        <div class="flex items-center justify-between mb-3 px-2">
                            <h4 class="text-xs font-bold text-white/40 uppercase tracking-wider">客户端</h4>
                            <button @click="openInvite('client')" class="text-blue-400 hover:text-blue-300 text-xs font-medium">+ 邀请</button>
                        </div>
                        <div class="space-y-2">
                            <div v-for="client in clientMembers" :key="client.memberId" class="group flex items-center justify-between p-2 rounded-xl hover:bg-white/5 transition-all">
                                <div class="flex items-center gap-3 min-w-0">
                                    <img :src="getAgentAvatar(client.memberId)" class="w-8 h-8 rounded-lg border border-blue-500/20" alt="" />
                                    <span class="text-sm font-medium truncate">{{ client.memberName }}</span>
                                </div>
                                <button @click="removeMember(client.memberId)" class="opacity-0 group-hover:opacity-100 p-1 text-slate-500 hover:text-blue-500 hover:bg-blue-500/10 rounded transition-all">🗑</button>
                            </div>
                        </div>
                    </section>

                    <section>
                        <div class="flex items-center justify-between mb-3 px-2">
                            <h4 class="text-xs font-bold text-white/40 uppercase tracking-wider">智能体</h4>
                            <button @click="openInvite('agent')" class="text-blue-400 hover:text-blue-300 text-xs font-medium">+ 邀请</button>
                        </div>
                        <div class="space-y-2">
                            <div v-for="agent in agentMembers" :key="agent.memberId" class="group flex items-center justify-between p-2 rounded-xl hover:bg-white/5 transition-all">
                                <div class="flex items-center gap-3 min-w-0">
                                    <img :src="getAgentAvatar(agent.memberId)" class="w-8 h-8 rounded-lg border border-blue-500/20" alt="" />
                                    <span class="text-sm font-medium truncate">{{ agent.memberName }}</span>
                                </div>
                                <button @click="removeMember(agent.memberId)" class="opacity-0 group-hover:opacity-100 p-1 text-slate-500 hover:text-blue-500 hover:bg-blue-500/10 rounded transition-all">🗑</button>
                            </div>
                        </div>
                    </section>

                    <section>
                        <h4 class="text-xs font-bold text-white/40 uppercase tracking-wider mb-3 px-2">在线用户</h4>
                        <div class="space-y-2">
                            <div v-for="human in humanMembers" :key="human.memberId" class="flex items-center gap-3 p-2">
                                <div class="w-8 h-8 rounded-lg bg-blue-500/20 text-blue-400 flex items-center justify-center font-bold text-xs border border-blue-500/30">
                                    {{ human.memberName?.charAt(0) }}
                                </div>
                                <span class="text-sm font-medium">{{ human.memberName }}</span>
                            </div>
                        </div>
                    </section>
                </div>
            </aside>
        </transition>

        <div v-if="showWinnerModal"
             class="fixed inset-0 z-40 grid place-items-center bg-black/60 backdrop-blur-sm p-4"
             @click.self="dismissWinnerModal">
            <div class="w-full max-w-md rounded-3xl border border-white/10 bg-slate-900 shadow-2xl overflow-hidden">
                <div class="px-6 py-5 border-b border-white/5">
                    <p class="text-[10px] uppercase tracking-widest text-blue-400 font-bold">ROUND END</p>
                    <h3 class="mt-2 text-lg font-bold text-white">请选择本轮胜方</h3>
                </div>
                <div class="px-6 py-5 space-y-3">
                    <button @click="declareDebateWinner('PRO')"
                            class="w-full px-4 py-3 rounded-2xl bg-blue-500 text-slate-900 text-sm font-bold hover:bg-blue-400 transition-all">
                        判定正方胜
                    </button>
                    <button @click="declareDebateWinner('CON')"
                            class="w-full px-4 py-3 rounded-2xl border border-white/10 text-sm font-bold text-white hover:bg-white/5 transition-all">
                        判定反方胜
                    </button>
                </div>
            </div>
        </div>

        <!-- 邀请弹窗 -->
        <div v-if="showInviteModal" class="fixed inset-0 z-50 grid place-items-center bg-black/70 backdrop-blur-md p-4" @click.self="showInviteModal = false">
            <div class="w-full max-w-lg bg-slate-900 rounded-3xl border border-white/10 shadow-2xl overflow-hidden flex flex-col max-h-[80vh]">
                <div class="p-6 border-b border-white/5 flex items-center justify-between">
                    <div>
                        <h3 class="text-xl font-bold text-blue-400">邀请成员</h3>
                        <p class="text-xs text-white/40 mt-1">{{ inviteMode === 'client' ? '邀请客户端加入' : '邀请智能体加入' }}</p>
                    </div>
                    <button @click="showInviteModal = false" class="text-white/40 hover:text-white">×</button>
                </div>
                
                <div class="flex-1 overflow-y-auto p-4 grid grid-cols-2 gap-3">
                    <div v-for="item in inviteList" :key="inviteMode === 'client' ? item.clientId : item.agentId" 
                         @click="inviteMode === 'client' ? addClient(item) : addAgent(item)"
                         class="p-4 rounded-2xl border border-white/5 bg-white/5 hover:border-blue-500/40 hover:bg-blue-500/5 cursor-pointer transition-all group">
                        <div class="flex flex-col items-center text-center gap-3">
                            <img :src="getAgentAvatar(inviteMode === 'client' ? item.clientId : item.agentId)" class="w-16 h-16 rounded-2xl border border-white/10 group-hover:scale-105 transition-transform" />
                            <div class="font-bold text-sm text-white">{{ inviteMode === 'client' ? (item.clientName || item.clientId) : (item.agentName || item.agentId) }}</div>
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
    chatRoomArbitratorSet,
    chatRoomArbitratorRemove,
    chatRoomDebateStart,
    chatRoomDebateDeclareWinner,
    chatRoomDebateNextRound,
    chatRoomDebateStop,
    chatRoomDebateStatus,
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

const normalizeMemberType = (memberType) => String(memberType || '').toUpperCase();
const resolveMemberId = (member) => member?.memberId || member?.clientId || member?.agentId || '';
const resolveMemberName = (member) => member?.memberName || member?.clientName || member?.agentName || resolveMemberId(member);

const normalizedMembers = computed(() => (members.value || [])
    .map(item => ({
        ...item,
        memberId: resolveMemberId(item),
        memberName: resolveMemberName(item),
        memberType: normalizeMemberType(item?.memberType)
    }))
    .filter(item => Boolean(item.memberId)));

const agentMembers = computed(() => normalizedMembers.value.filter(m => m.memberType === 'AGENT'));
const clientMembers = computed(() => normalizedMembers.value.filter(m => m.memberType === 'CLIENT'));
const humanMembers = computed(() => normalizedMembers.value.filter(m => m.memberType === 'USER'));

const inputText = ref('');
const showMemberDrawer = ref(false);
const showInviteModal = ref(false);
const showDebatePanel = ref(false);
const debatePanelInitialized = ref(false);
const showWinnerModal = ref(false);
const winnerModalToken = ref('');
const loadingHistory = ref(false);
const hasMoreHistory = ref(true);
const socketReady = ref(false);
const availableClients = ref([]);
const availableAgents = ref([]);
const inviteMode = ref('client');
const debateStatus = ref(null);
const debateLoading = ref(false);
const debateTopic = ref('');
const debateTurns = ref(6);
const selectedArbitratorId = ref('');
const selectedProIds = ref([]);
const selectedConIds = ref([]);

const inviteList = computed(() => (inviteMode.value === 'client' ? availableClients.value : availableAgents.value));
const isDebateRunning = computed(() => ['RUNNING', 'ROUND_END'].includes(debateStatus.value?.status || ''));
const waitingForWinner = computed(() => Boolean(debateStatus.value?.waitingForWinner));
const canDeclareWinner = computed(() => {
    if (typeof debateStatus.value?.canDeclareWinner === 'boolean') {
        return debateStatus.value.canDeclareWinner;
    }
    return Boolean(debateStatus.value?.status === 'ROUND_END' && waitingForWinner.value);
});
const canStartNextRound = computed(() => {
    if (typeof debateStatus.value?.canStartNextRound === 'boolean') {
        return debateStatus.value.canStartNextRound;
    }
    const winners = debateStatus.value?.roundWinners || {};
    const currentRound = debateStatus.value?.currentRound || 0;
    const hasWinner = currentRound > 0 && Boolean(winners[String(currentRound)]);
    return Boolean(debateStatus.value?.status === 'ROUND_END' && !waitingForWinner.value && hasWinner);
});
const canStopDebate = computed(() => {
    if (typeof debateStatus.value?.canStopDebate === 'boolean') {
        return debateStatus.value.canStopDebate;
    }
    return isDebateRunning.value;
});
const debatePanelVisible = computed(() => Boolean(showDebatePanel.value));
const debateClientOptions = computed(() => clientMembers.value.map(item => ({
    id: item.memberId,
    name: item.memberName || item.memberId
})));
const availableDebaterOptions = computed(() => debateClientOptions.value.filter(item => item.id !== selectedArbitratorId.value));
const clientNameMap = computed(() => debateClientOptions.value.reduce((acc, item) => {
    acc[item.id] = item.name;
    return acc;
}, {}));
const selectedArbitratorName = computed(() => clientNameMap.value[selectedArbitratorId.value] || '');
const selectedProMembers = computed(() => selectedProIds.value.map(id => ({
    clientId: id,
    clientName: clientNameMap.value[id] || id
})));
const selectedConMembers = computed(() => selectedConIds.value.map(id => ({
    clientId: id,
    clientName: clientNameMap.value[id] || id
})));
const debateStatusLabel = computed(() => {
    const status = debateStatus.value?.status;
    if (status === 'RUNNING') return '进行中';
    if (status === 'ROUND_END') return waitingForWinner.value ? '等待裁决' : '待开启下一轮';
    if (status === 'FINISHED') return '已结束';
    return '未开始';
});
const debateStatusPillClass = computed(() => {
    const status = debateStatus.value?.status;
    if (status === 'RUNNING') return 'border-blue-500/30 bg-blue-500/10 text-blue-400';
    return 'border-white/10 bg-white/5 text-white/40';
});
const debateRoundResultLabel = computed(() => {
    const winners = debateStatus.value?.roundWinners || {};
    const currentRound = debateStatus.value?.currentRound || debateStatus.value?.pendingRoundNumber;
    if (currentRound && winners[String(currentRound)]) {
        return winners[String(currentRound)] === 'PRO' ? '本轮正方获胜' : '本轮反方获胜';
    }
    if (waitingForWinner.value) return '等待用户裁决';
    if (Object.keys(winners).length > 0) return '已有轮次结果';
    return '暂无结果';
});
const debateProgressLabel = computed(() => {
    if (!debateStatus.value?.sessionId) return '未开始';
    const round = debateStatus.value.currentRound || debateStatus.value.pendingRoundNumber || 0;
    const turn = debateStatus.value.currentTurn || 0;
    const total = debateStatus.value.turnsPerRound || 0;
    return `第 ${round} 轮 · ${turn}/${total} 次发言`;
});

const messageListRef = ref(null);
const bottomAnchor = ref(null);
const inputRef = ref(null);

// -------------------- @ 选人逻辑状态 --------------------
const showAtList = ref(false);
const atSearchText = ref('');
const selectedAtMembers = ref([]);

const filteredAtMembers = computed(() => {
    const bots = normalizedMembers.value.filter(m => m.memberType === 'CLIENT');
    if (!atSearchText.value) return bots;
    return bots.filter(m => (m.memberName || '').toLowerCase().includes(atSearchText.value.toLowerCase()));
});

const handleInput = (e) => {
    const text = inputText.value;
    const cursorPosition = e.target.selectionStart;
    const lastAtIdx = text.lastIndexOf('@', cursorPosition - 1);
    if (lastAtIdx !== -1) {
        const searchStr = text.slice(lastAtIdx + 1, cursorPosition);
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
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
        return;
    }
    if (e.key === 'Backspace') {
        const text = inputText.value;
        const cursorPosition = inputRef.value.selectionStart;
        for (const member of selectedAtMembers.value) {
            const tagText = `@${member.name} `;
            const tagIndex = text.lastIndexOf(tagText, cursorPosition);
            if (tagIndex !== -1 && cursorPosition > tagIndex && cursorPosition <= tagIndex + tagText.length) {
                e.preventDefault();
                const newText = text.slice(0, tagIndex) + text.slice(tagIndex + tagText.length);
                inputText.value = newText;
                selectedAtMembers.value = selectedAtMembers.value.filter(m => m.id !== member.id);
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
        const beforeAt = text.slice(0, lastAtIdx);
        const afterCursor = text.slice(cursorPosition);
        const replaceText = `@${agent.memberName} `;
        inputText.value = beforeAt + replaceText + afterCursor;
        if (!selectedAtMembers.value.find(m => m.id === agent.memberId)) {
            selectedAtMembers.value.push({ id: agent.memberId, name: agent.memberName });
        }
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
    if (msg.senderType !== 'CLIENT') {
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

const initWebSocket = () => {
    if (socket) {
        socket.close();
    }
    const username = currentUser.value?.username || currentUser.value?.userName;
    if (!username) return;
    const wsUrl = `ws://localhost:8066/miniagent/api/v1/ws/room/${roomId.value}/${username}`;
    socket = new WebSocket(wsUrl);
    socket.onopen = () => {
        socketReady.value = true;
    };
    socket.onmessage = (event) => {
        try {
            const wsEvent = JSON.parse(event.data);
            handleIncomingEvent(wsEvent);
        } catch (e) {}
    };
    socket.onclose = () => {
        socketReady.value = false;
    };
};

const handleIncomingEvent = (wsEvent) => {
    const { eventType, payload } = wsEvent;
    if (eventType === 'USER_MSG' || eventType === 'AGENT_MSG' || eventType === 'CLIENT_MSG' || eventType === 'SYSTEM_NOTICE') {
        roomStore.pushMessage(payload);
        if (eventType === 'SYSTEM_NOTICE' && shouldRefreshDebateStatus(payload)) {
            loadDebateStatus();
        }
        scrollToBottom();
    }
};

const sendMessage = () => {
    if (!inputText.value.trim() || !socketReady.value) return;
    const atMemberIdsStr = selectedAtMembers.value.map(m => m.id).join(',');
    const payload = {
        content: inputText.value.trim(),
        traceId: 'trace_' + Date.now(),
        atMemberIds: atMemberIdsStr
    };
    socket.send(JSON.stringify(payload));
    inputText.value = '';
    selectedAtMembers.value = [];
    showAtList.value = false;
    nextTick(() => scrollToBottom());
};

const loadMembers = async () => {
    try {
        const res = await chatRoomMemberList(roomId.value);
        if (res.code === 200) {
            roomStore.setMembers(res.data);
        }
    } catch (e) {}
};

const loadHistory = async (isInitial = false) => {
    if (loadingHistory.value) return;
    loadingHistory.value = true;
    try {
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
                roomStore.setMessages(newMsgs.reverse());
                nextTick(() => scrollToBottom('auto'));
            } else if (newMsgs.length > 0) {
                roomStore.prependMessages(newMsgs.reverse());
            }
        }
    } catch (e) {} finally {
        loadingHistory.value = false;
    }
};

const loadAvailableClients = async () => {
    try {
        const res = await listClients();
        if (res.code === 200) {
            availableClients.value = res.data || [];
        }
    } catch (e) {}
};

const loadAvailableAgents = async () => {
    try {
        const res = await listAgents();
        if (res.code === 200) {
            availableAgents.value = res.data || [];
        }
    } catch (e) {}
};

const syncDebateFormFromStatus = (status) => {
    if (!status) return;
    selectedArbitratorId.value = status.arbitratorClientId || selectedArbitratorId.value || '';
    if (status.sessionId) {
        debateTopic.value = status.topic || debateTopic.value;
        debateTurns.value = status.turnsPerRound || debateTurns.value || 6;
        selectedProIds.value = Array.isArray(status.proMembers) ? status.proMembers.map(item => item.clientId) : [];
        selectedConIds.value = Array.isArray(status.conMembers) ? status.conMembers.map(item => item.clientId) : [];
    }
};

const buildWinnerModalKey = (status) => {
    if (!status?.waitingForWinner) return '';
    const roundNumber = status.pendingRoundNumber || status.currentRound || 0;
    if (!status.sessionId || !roundNumber) return '';
    return `${status.sessionId}:${roundNumber}`;
};

const syncDebatePanelState = (status) => {
    const hasActiveDebate = Boolean(status?.sessionId || status?.waitingForWinner);
    if (hasActiveDebate && !debatePanelInitialized.value) {
        showDebatePanel.value = true;
        debatePanelInitialized.value = true;
    }
};

const syncWinnerModalState = (status) => {
    const modalKey = buildWinnerModalKey(status);
    if (!modalKey) {
        showWinnerModal.value = false;
        winnerModalToken.value = '';
        return;
    }
    if (winnerModalToken.value !== modalKey) {
        winnerModalToken.value = modalKey;
        showWinnerModal.value = true;
    }
};

const dismissWinnerModal = () => {
    showWinnerModal.value = false;
};

const loadDebateStatus = async () => {
    if (!roomId.value) return;
    try {
        const res = await chatRoomDebateStatus(roomId.value);
        if (res.code === 200) {
            debateStatus.value = res.data || null;
            syncDebateFormFromStatus(debateStatus.value);
            syncDebatePanelState(debateStatus.value);
            syncWinnerModalState(debateStatus.value);
        }
    } catch (e) {
        debateStatus.value = null;
        showWinnerModal.value = false;
    }
};

const runDebateAction = async (action) => {
    if (debateLoading.value) return;
    debateLoading.value = true;
    try {
        await action();
        await loadDebateStatus();
    } finally {
        debateLoading.value = false;
    }
};

const setDebateArbitrator = async () => {
    if (!selectedArbitratorId.value) {
        toast.show('请先选择仲裁者');
        return;
    }
    await runDebateAction(async () => {
        const res = await chatRoomArbitratorSet({
            roomId: roomId.value,
            clientId: selectedArbitratorId.value
        });
        if (res.code !== 200) {
            throw new Error(res.info || '设置仲裁者失败');
        }
        toast.show('仲裁者设置成功');
    }).catch((e) => toast.show(e.message || '设置仲裁者失败'));
};

const removeDebateArbitrator = async () => {
    await runDebateAction(async () => {
        const res = await chatRoomArbitratorRemove(roomId.value);
        if (res.code !== 200) {
            throw new Error(res.info || '移除仲裁者失败');
        }
        toast.show('已移除仲裁者');
    }).catch((e) => toast.show(e.message || '移除仲裁者失败'));
};

const toggleDebater = (side, clientId) => {
    if (clientId === selectedArbitratorId.value) {
        toast.show('仲裁者不能同时作为辩手');
        return;
    }
    const target = side === 'PRO' ? selectedProIds : selectedConIds;
    const opposite = side === 'PRO' ? selectedConIds : selectedProIds;
    if (target.value.includes(clientId)) {
        target.value = target.value.filter(id => id !== clientId);
        return;
    }
    opposite.value = opposite.value.filter(id => id !== clientId);
    target.value = [...target.value, clientId];
};

const startDebateFlow = async () => {
    if (!selectedArbitratorId.value) {
        toast.show('请先设置仲裁者');
        return;
    }
    if (!debateTopic.value.trim()) {
        toast.show('请输入辩题');
        return;
    }
    if (!selectedProIds.value.length || !selectedConIds.value.length) {
        toast.show('请至少选择一名正方和一名反方辩手');
        return;
    }
    await runDebateAction(async () => {
        const res = await chatRoomDebateStart({
            roomId: roomId.value,
            topic: debateTopic.value.trim(),
            proClientIds: selectedProIds.value,
            conClientIds: selectedConIds.value,
            turnsPerRound: Number(debateTurns.value) || 6
        });
        if (res.code !== 200) {
            throw new Error(res.info || '开始辩论失败');
        }
        toast.show('辩论已开始');
    }).catch((e) => toast.show(e.message || '开始辩论失败'));
};

const declareDebateWinner = async (winnerSide) => {
    await runDebateAction(async () => {
        const res = await chatRoomDebateDeclareWinner({
            roomId: roomId.value,
            winnerSide
        });
        if (res.code !== 200) {
            throw new Error(res.info || '宣布胜方失败');
        }
        showWinnerModal.value = false;
        toast.show(`已宣布${winnerSide === 'PRO' ? '正方' : '反方'}获胜`);
    }).catch((e) => toast.show(e.message || '宣布胜方失败'));
};

const startDebateNextRound = async () => {
    await runDebateAction(async () => {
        const res = await chatRoomDebateNextRound({ roomId: roomId.value });
        if (res.code !== 200) {
            throw new Error(res.info || '开始下一轮失败');
        }
        toast.show('下一轮已开始');
    }).catch((e) => toast.show(e.message || '开始下一轮失败'));
};

const stopDebateFlow = async () => {
    await runDebateAction(async () => {
        const res = await chatRoomDebateStop({ roomId: roomId.value });
        if (res.code !== 200) {
            throw new Error(res.info || '停止辩论失败');
        }
        showDebatePanel.value = false;
        showWinnerModal.value = false;
        winnerModalToken.value = '';
        toast.show('辩论已结束');
    }).catch((e) => toast.show(e.message || '停止辩论失败'));
};

const extractSystemNoticeType = (msg) => {
    try {
        const ext = msg?.extData ? JSON.parse(msg.extData) : null;
        return ext?.noticeType || '';
    } catch (e) {
        return '';
    }
};

const shouldRefreshDebateStatus = (msg) => {
    const noticeType = extractSystemNoticeType(msg);
    return ['DEBATE_START', 'HOST_INTRO', 'ROUND_END', 'ROUND_WINNER', 'ROUND_NEXT', 'SPEAKER_ERROR', 'SLOT_SKIPPED', 'DEBATE_STOP'].includes(noticeType);
};

const parseSystemNoticeType = (msg) => {
    const noticeType = extractSystemNoticeType(msg);
    if (noticeType === 'DEBATE_START') return '辩论开始';
    if (noticeType === 'HOST_INTRO') return '主持调度';
    if (noticeType === 'ROUND_END') return '本轮结束';
    if (noticeType === 'ROUND_WINNER') return '本轮结果';
    if (noticeType === 'ROUND_NEXT') return '下一轮开始';
    if (noticeType === 'SPEAKER_ERROR') return '发言异常';
    if (noticeType === 'SLOT_SKIPPED') return '槽位跳过';
    if (noticeType === 'DEBATE_STOP') return '辩论结束';
    if (noticeType === 'MEMBER_JOIN') return '成员入场';
    if (noticeType === 'CLIENT_EXECUTION_ERROR') return '响应失败';
    if (noticeType === 'MULTI_AT_ITEM_FAILED') return '响应未完成';
    return '系统通知';
};

const systemNoticeCardClass = (msg) => {
    const noticeType = extractSystemNoticeType(msg);
    if (noticeType === 'MEMBER_JOIN') {
        return 'max-w-[90%] rounded-2xl border border-blue-500/20 bg-blue-500/5 px-4 py-2 text-left';
    }
    return 'max-w-[86%] rounded-xl border border-white/5 bg-white/5 px-3 py-2 text-left';
};

const systemNoticeLabelClass = (msg) => {
    const noticeType = extractSystemNoticeType(msg);
    if (noticeType === 'MEMBER_JOIN') return 'text-blue-400';
    return 'text-blue-400/60';
};

const systemNoticeContentClass = (msg) => {
    const noticeType = extractSystemNoticeType(msg);
    if (noticeType === 'MEMBER_JOIN') return 'text-white/80';
    return 'text-white/60';
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

const scrollToBottom = (behavior = 'smooth') => {
    nextTick(() => {
        bottomAnchor.value?.scrollIntoView({ behavior });
    });
};

const handleScroll = (e) => {
    const el = e.target;
    if (el.scrollTop < 20 && hasMoreHistory.value && !loadingHistory.value) {
        const oldHeight = el.scrollHeight;
        loadHistory().then(() => {
            nextTick(() => {
                const newHeight = el.scrollHeight;
                el.scrollTop = newHeight - oldHeight;
            });
        });
    }
};

const getAgentAvatar = (clientId) => {
    const styles = ['bottts', 'identicon', 'avataaars', 'pixel-art', 'big-smile'];
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

onMounted(() => {
    roomStore.setCurrentRoomId(roomId.value);
    loadMembers();
    loadHistory(true);
    loadDebateStatus();
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
        showDebatePanel.value = false;
        debatePanelInitialized.value = false;
        showWinnerModal.value = false;
        winnerModalToken.value = '';
        loadMembers();
        loadHistory(true);
        loadDebateStatus();
        initWebSocket();
    }
});

watch(selectedArbitratorId, (nextArbitratorId) => {
    if (!nextArbitratorId) return;
    selectedProIds.value = selectedProIds.value.filter(id => id !== nextArbitratorId);
    selectedConIds.value = selectedConIds.value.filter(id => id !== nextArbitratorId);
});
</script>

<style scoped>
.slide-enter-active, .slide-leave-active {
    transition: transform 0.3s ease;
}
.slide-enter-from, .slide-leave-to {
    transform: translateX(100%);
}
.scrollbar-none::-webkit-scrollbar {
    display: none;
}
.scrollbar-none {
    -ms-overflow-style: none;
    scrollbar-width: none;
}
</style>
