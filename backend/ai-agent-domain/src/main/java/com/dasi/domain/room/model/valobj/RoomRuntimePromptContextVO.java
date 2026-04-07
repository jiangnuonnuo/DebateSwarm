package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: xerina
 * @Description: 房间对话运行时提示词上下文，仅用于单次请求拼装，不入库
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomRuntimePromptContextVO {

    private String roomId;

    private String sessionId;

    private String stage;

    private String traceId;

    private String eventType;

    private String senderId;

    private String senderName;

    private String atMemberIds;

    private String runtimeHint;
}
