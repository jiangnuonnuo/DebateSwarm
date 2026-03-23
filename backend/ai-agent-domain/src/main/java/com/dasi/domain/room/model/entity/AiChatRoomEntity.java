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
 * @CreateTime: 2026-03-23  11:10
 * @Description: 聊天室领域实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRoomEntity {

    /** 房间业务唯一ID */
    private String roomId;

    /** 房间名称 */
    private String roomName;

    /** 房间描述 */
    private String roomDesc;

    /** 创建者用户ID */
    private Long ownerId;

    /** 类型: GROUP, PRIVATE */
    private String roomType;

    /** 扩展配置(JSON: 场景规则等) */
    private String extConfig;

    /** 状态: 0-激活, 1-归档 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
