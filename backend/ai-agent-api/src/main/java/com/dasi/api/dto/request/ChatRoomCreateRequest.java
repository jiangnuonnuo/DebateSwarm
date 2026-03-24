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
 * @CreateTime: 2026-03-24  18:00
 * @Description: 创建聊天室请求对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 房间名称 */
    private String roomName;

    /** 房间描述 */
    private String roomDesc;

    /** 房间类型: GROUP, PRIVATE */
    private String roomType;

}
