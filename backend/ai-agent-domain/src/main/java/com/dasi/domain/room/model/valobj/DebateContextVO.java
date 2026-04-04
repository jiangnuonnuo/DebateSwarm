package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: xerina
 * @Description: 辩论会话上下文信息 (Value Object - 在单场辩论中保持不变)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DebateContextVO {

    /** 辩论会话ID */
    private String sessionId;

    /** 房间ID */
    private String roomId;

    /** 辩论主题 */
    private String topic;

    /** 仲裁者 clientId */
    private String arbitratorClientId;

    /** 正方成员列表 */
    private List<String> proClientIds;

    /** 反方成员列表 */
    private List<String> conClientIds;

    /** 当前轮次 */
    private Integer currentRound;

    /** 当前发言次序 */
    private Integer currentTurn;

    /** 每轮固定对话次数 */
    private Integer turnsPerRound;

    /** 会话状态 */
    private String status;

}
