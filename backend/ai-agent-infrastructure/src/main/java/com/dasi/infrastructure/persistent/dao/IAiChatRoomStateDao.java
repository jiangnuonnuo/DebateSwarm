package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiChatRoomState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @Autor：xerina
 * @description：聊天室业务状态/流转仓储接口
 */
@Mapper
public interface IAiChatRoomStateDao {

    /**
     * 初始化房间状态
     */
    void insert(AiChatRoomState aiChatRoomState);

    /**
     * 查询当前房间的业务状态 (按需查询：current_stage, active_member_id, public_data, private_data, version)
     */
    AiChatRoomState queryByRoomId(@Param("roomId") String roomId);

    /**
     * 携带乐观锁的版本更新状态
     * UPDATE ... SET ... version = version + 1 WHERE room_id = #{roomId} AND version = #{version}
     */
    int updateStateWithLock(AiChatRoomState aiChatRoomState);

    /**
     * 强制覆盖更新状态（不校验 version）。
     */
    int updateStateForce(AiChatRoomState aiChatRoomState);

    /**
     * 物理/逻辑删除状态记录 (通常随房间一起逻辑删除)
     */
    void deleteByRoomId(@Param("roomId") String roomId);
}
