package com.dasi.domain.room.apapter.repository;

import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;

import java.util.List;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.apapter.repository
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:07
 * @Description: 聊天室仓储接口 (领域层定义)
 */
public interface IChatRoomRepository {

    /**
     * 查询成员在该房间的昵称
     */
    String queryMemberName(String roomId, String memberId);

    /**
     * 查询房间名称
     */
    String queryRoomName(String roomId);

    /**
     * 查询构建上下文所需的最新消息流 (返回领域对象 Entity)
     */
    List<AiChatRoomMessageEntity> queryContextMessages(String roomId, Integer limit);
}
