package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Autor：xerina
 * @description：聊天室场景业务状态实体类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatRoomState {
    /** 自增主键 */
    private Long id;

    /** 房间唯一ID */
    private String roomId;

    /** 当前业务阶段 (如: PREPARING, DEALING, ACTION, SETTLING) */
    private String currentStage;

    /** 当前令牌持有者 (该谁操作了) */
    private String activeMemberId;

    /** 操作截止时间 */
    private LocalDateTime actionDeadline;

    /** 公共可见数据 (JSON) */
    private String publicData;

    /** 加密私有数据 (JSON) */
    private String privateData;

    /** 当前局数/轮数 */
    private Integer roundNumber;

    /** 触发最后一次状态变更的消息ID */
    private String lastActionMsgId;

    /** 乐观锁版本号 */
    private Integer version;

    /** 伪删除标记 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
