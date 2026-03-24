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
 * @CreateTime: 2026-03-24  18:10
 * @Description: 聊天室消息游标查询请求对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomCursorRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 房间ID */
    private String roomId;

    /** 游标时间戳 (毫秒级，查询该时间之前的消息) */
    private Long cursorTime;

    /** 每页大小 (默认 20) */
    @Builder.Default
    private Integer pageSize = 20;

}
