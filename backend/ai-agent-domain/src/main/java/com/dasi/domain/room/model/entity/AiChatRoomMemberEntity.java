package com.dasi.domain.room.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.model.entity
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:15
 * @Description: 聊天室成员领域实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRoomMemberEntity {

    /** 房间ID */
    private String roomId;

    /** 成员ID (UserID或AgentID) */
    private String memberId;

    /** 类型: USER, AGENT */
    private String memberType;

    /** 昵称(用于构建上下文) */
    private String memberName;

    /** Agent独立SessionID (用于隔离私有上下文) */
    private String agentSessionId;

    /** 加入时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
