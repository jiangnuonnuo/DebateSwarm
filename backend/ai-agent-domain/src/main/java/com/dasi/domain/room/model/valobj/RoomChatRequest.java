package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.model.valobj
 * @Author: xerina
 * @CreateTime: 2026-03-23  18:55
 * @Description: 聊天室对话请求 (上行负载)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoomChatRequest {

    /** 房间ID */
    private String roomId;

    /** 发送者ID (UserID) */
    private String userId;

    /** 消息内容 */
    private String content;

    /** 追踪ID (可选) */
    private String traceId;

}
