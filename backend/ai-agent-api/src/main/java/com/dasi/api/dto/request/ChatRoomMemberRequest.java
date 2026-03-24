package com.dasi.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.api.dto.request
 * @Author: xerina
 * @CreateTime: 2026-03-24  18:05
 * @Description: 聊天室成员操作请求对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMemberRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 房间ID */
    private String roomId;

    /** 成员ID (UserID 或 ClientID) */
    private String memberId;

    /** 成员类型: USER, AGENT */
    private String memberType;

}
