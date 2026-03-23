package com.dasi.domain.room.service;

import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;

import java.util.List;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:04
 * @Description: 房间管理服务接口
 */
public interface IRoomAdminService {

    /**
     * 创建房间
     * @param roomEntity 房间信息
     * @return 成功返回房间ID
     */
    String createRoom(AiChatRoomEntity roomEntity);

    /**
     * 更新房间信息
     * @param roomEntity 房间信息
     * @return 成功与否
     */
    boolean updateRoom(AiChatRoomEntity roomEntity);

    /**
     * 删除房间 (逻辑删除)
     * @param roomId 房间ID
     * @return 成功与否
     */
    boolean deleteRoom(String roomId);

    /**
     * 根据ID查询房间
     * @param roomId 房间ID
     * @return 房间实体
     */
    AiChatRoomEntity queryRoomById(String roomId);

    /**
     * 查询用户拥有的所有房间
     * @param ownerId 创建者ID
     * @return 房间列表
     */
    List<AiChatRoomEntity> queryRoomsByOwnerId(Long ownerId);

    /**
     * 智能体/用户加入房间
     * @param memberEntity 成员信息
     * @return 成功与否
     */
    boolean joinRoom(AiChatRoomMemberEntity memberEntity);

    /**
     * 移除房间成员
     * @param roomId   房间ID
     * @param memberId 成员ID
     * @return 成功与否
     */
    boolean leaveRoom(String roomId, String memberId);

    /**
     * 查询房间内的所有机器人智能体
     * @param roomId 房间ID
     * @return 智能体成员列表
     */
    List<AiChatRoomMemberEntity> queryAgentsInRoom(String roomId);

    /**
     * 查询房间内的所有成员
     * @param roomId 房间ID
     * @return 成员列表
     */
    List<AiChatRoomMemberEntity> queryMembersInRoom(String roomId);

}
