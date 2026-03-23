package com.dasi.domain.room.apapter.repository;

import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;

import java.util.List;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.apapter.repository
 * @Author: xerina
 * @CreateTime: 2026-03-23  10:20
 * @Description: 聊天室仓库接口 (Domain Layer Contract)
 */
public interface IChatRoomRepository {

    /**
     * 保存/更新房间信息
     */
    void saveRoom(AiChatRoomEntity roomEntity);

    /**
     * 逻辑删除房间
     */
    void deleteRoom(String roomId);

    /**
     * 根据ID查询房间
     */
    AiChatRoomEntity queryRoomById(String roomId);

    /**
     * 查询用户的所有房间
     */
    List<AiChatRoomEntity> queryRoomsByOwnerId(Long ownerId);

    /**
     * 加入/更新房间成员
     */
    void saveMember(AiChatRoomMemberEntity memberEntity);

    /**
     * 移除房间成员
     */
    void deleteMember(String roomId, String memberId);

    /**
     * 查询房间内的所有成员
     */
    List<AiChatRoomMemberEntity> queryMembersByRoomId(String roomId);

    /**
     * 查询房间内的所有智能体成员
     */
    List<AiChatRoomMemberEntity> queryAgentsByRoomId(String roomId);

    /**
     * 查询成员在房间的昵称
     */
    String queryMemberName(String roomId, String memberId);

    /**
     * 查询房间名称
     */
    String queryRoomName(String roomId);

    /**
     * 保存聊天消息
     */
    void saveMessage(AiChatRoomMessageEntity messageEntity);

    /**
     * 查询构建上下文所需的最新消息流 (返回领域对象 Entity)
     */
    List<AiChatRoomMessageEntity> queryContextMessages(String roomId, Integer limit);

    String queryExtConfigByRoomId(String roomId);
}
