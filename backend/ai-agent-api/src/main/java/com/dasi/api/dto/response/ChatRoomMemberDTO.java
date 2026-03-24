package com.dasi.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.api.dto.response
 * @Author: xerina
 * @CreateTime: 2026-03-24  19:00
 * @Description: 聊天室成员视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMemberDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成员ID (UserID 或 ClientID) */
    private String memberId;

    /** 成员名称 (昵称) */
    private String memberName;

    /** 成员类型: USER, AGENT */
    private String memberType;

    /** 角色标识 (如果是 Agent) */
    private String memberRole;

}
