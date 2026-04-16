package com.dasi.domain.room.service.dispatch;

import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.model.valobj.RoomDebateStateVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: xerina
 * @Description: 调度上下文
 * 职责：在规则树节点间传递状态，缓存已加载的领域对象，存储最终决策。
 */
@Data
public class DispatchContext {

    public static final String PHASE_FREE_CHAT = "FREE_CHAT";
    public static final String PHASE_RUNNING = "RUNNING";
    public static final String PHASE_ROUND_END_WAIT_WINNER = "ROUND_END_WAIT_WINNER";
    public static final String PHASE_INTERMISSION = "INTERMISSION";
    public static final String PHASE_FINISHED = "FINISHED";

    /** 活跃的辩论会话 (缓存避免重复查询) */
    private DebateSessionEntity debateSession;

    /** 房间辩论运行态快照 */
    private RoomDebateStateVO roomDebateState;

    /** 当前调度阶段（由根节点统一判定） */
    private String debatePhase = PHASE_FREE_CHAT;

    /** 本次消息中合法且按输入顺序去重后的 @ CLIENT 候选 */
    private List<AiChatRoomMemberEntity> mentionCandidates = new ArrayList<>();

    /** 最终决策结果 */
    private DispatchDecisionVO decision;

    /** Multi-At 批量决策结果 (仅自由聊天模式启用) */
    private List<DispatchDecisionVO> decisions = new ArrayList<>();

    /** 是否进入 Multi-At 批处理模式 */
    private boolean multiAtMode;

    /** 扩展属性位 */
    private final Map<String, Object> metadata = new HashMap<>();

    /** 是否终止后续链路 */
    private boolean terminateChain;

    /**
     * 是否已经产生决策
     */
    public boolean hasDecision() {
        return decision != null;
    }

    /**
     * 是否已经产生批量决策
     */
    public boolean hasDecisions() {
        return decisions != null && !decisions.isEmpty();
    }

    /**
     * 是否已有任意可执行决策
     */
    public boolean hasAnyDecision() {
        return hasDecision() || hasDecisions();
    }

    /**
     * 存入扩展信息
     */
    public void putMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * 获取扩展信息
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        return (T) metadata.get(key);
    }

    public boolean isDebateRunningPhase() {
        return PHASE_RUNNING.equals(debatePhase);
    }

    public boolean isRoundEndWaitWinnerPhase() {
        return PHASE_ROUND_END_WAIT_WINNER.equals(debatePhase);
    }

    public boolean isIntermissionPhase() {
        return PHASE_INTERMISSION.equals(debatePhase);
    }

    public boolean hasMentionCandidates() {
        return mentionCandidates != null && !mentionCandidates.isEmpty();
    }
}
