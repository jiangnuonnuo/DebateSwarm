package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Autor：xerina
 * @description：聊天室成员实体类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatRoomMember {
    /** 自增主键 */
    private Long id;

    /** 房间ID */
    private String roomId;

    /** 成员ID */
    private String memberId;

    /** 类型: USER, AGENT */
    private String memberType;

    /** 昵称(用于上下文) */
    private String memberName;

    /** Agent独立SessionID */
    private String agentSessionId;

    /** 伪删除标记 */
    private Integer isDeleted;

    /** 加入时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
