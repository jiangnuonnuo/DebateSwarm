package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author: xerina
 * @Description: 辩论会话表持久化对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiDebateSession {
    /** 自增主键 */
    private Long id;

    /** 辩论会话业务ID (debate_xxx) */
    private String sessionId;

    /** 关联房间ID */
    private String roomId;

    /** 辩论主题 */
    private String topic;

    /** 仲裁者(主持人) CLIENT ID */
    private String arbitratorClientId;

    /** 正方CLIENT ID列表(逗号分隔) */
    private String proClientIds;

    /** 反方CLIENT ID列表(逗号分隔) */
    private String conClientIds;

    /** 每轮固定对话次数 */
    private Integer turnsPerRound;

    /** 当前轮次号 */
    private Integer currentRound;

    /** 当前轮内对话序号 */
    private Integer currentTurn;

    /** 每轮胜方记录(逗号分隔: PRO,CON,...) */
    private String roundWinners;

    /** 状态: PENDING/RUNNING/ROUND_END/FINISHED */
    private String status;

    /** 乐观锁版本号 */
    private Integer version;

    /** 逻辑删除标记 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
