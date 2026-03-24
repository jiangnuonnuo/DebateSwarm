package com.dasi.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.api.dto.response
 * @Author: xerina
 * @CreateTime: 2026-03-24  18:15
 * @Description: 聊天室视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 房间业务唯一ID */
    private String roomId;

    /** 房间名称 */
    private String roomName;

    /** 房间描述 */
    private String roomDesc;

    /** 创建者ID */
    private Long ownerId;

    /** 房间类型: GROUP, PRIVATE */
    private String roomType;

    /** 状态: 0-激活, 1-归档 */
    private Integer status;

    /** 创建时间 */
    private Date createTime;

}
