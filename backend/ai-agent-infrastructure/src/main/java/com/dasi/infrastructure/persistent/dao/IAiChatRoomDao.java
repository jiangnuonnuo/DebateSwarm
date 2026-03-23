package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiChatRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Autor：xerina
 * @description：聊天室基础信息仓储接口
 */
@Mapper
public interface IAiChatRoomDao {

    /**
     * 插入房间信息
     */
    void insert(AiChatRoom aiChatRoom);

    /**
     * 根据 roomId 查询房间基础信息 (按需查询：id, room_id, room_name, room_type, ext_config, status)
     */
    AiChatRoom queryRoomByRoomId(@Param("roomId") String roomId);

    /**
     * 分页查询用户创建的房间列表 (按需查询：room_id, room_name, room_type, status, create_time)
     */
    List<AiChatRoom> queryRoomListByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 更新房间基础信息
     */
    void updateRoomInfo(AiChatRoom aiChatRoom);

    /**
     * 逻辑删除房间
     */
    void deleteByRoomId(@Param("roomId") String roomId);
}
