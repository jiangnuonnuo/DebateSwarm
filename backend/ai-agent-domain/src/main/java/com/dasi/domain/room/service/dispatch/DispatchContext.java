package com.dasi.domain.room.service.dispatch;

import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: xerina
 * @Description: 调度上下文
 * 职责：在规则树节点间传递状态，缓存已加载的领域对象，存储最终决策。
 */
@Data
public class DispatchContext {

    /** 活跃的辩论会话 (缓存避免重复查询) */
    private DebateSessionEntity debateSession;

    /** 最终决策结果 */
    private DispatchDecisionVO decision;

    /** 扩展属性位 */
    private final Map<String, Object> metadata = new HashMap<>();

    /**
     * 是否已经产生决策
     */
    public boolean hasDecision() {
        return decision != null;
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
}
