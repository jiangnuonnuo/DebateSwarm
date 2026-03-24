package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiChatRoomMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Autor：xerina
 * @description：聊天室消息流仓储接口
 */
@Mapper
public interface IAiChatRoomMessageDao {

    /**
     * 插入消息记录
     */
    void insert(AiChatRoomMessage aiChatRoomMessage);

    /**
     * 分页/限额查询房间内的历史消息用于构建上下文 (按需查询：sender_name, message_role, content, sender_type)
     * 必须走 idx_room_create_deleted 索引
     */
    List<AiChatRoomMessage> queryContextMessages(@Param("roomId") String roomId, @Param("limit") Integer limit);

    /**
     * 查询某个发送者在所有房间的消息记录 (监控审计用)
     * 必须走 idx_sender_create 索引
     */
    List<AiChatRoomMessage> queryMessagesBySender(@Param("senderId") String senderId, @Param("limit") Integer limit);

    /**
     * 根据消息ID查询详情
     */
    AiChatRoomMessage queryMessageByMsgId(@Param("messageId") String messageId);

    /**
     * 游标查询聊天消息记录
     */
    List<AiChatRoomMessage> queryMessagesByCursor(@Param("roomId") String roomId, @Param("cursorTime") Long cursorTime, @Param("limit") Integer limit);

    /**
     * 逻辑删除消息
     */
    void deleteByMessageId(@Param("messageId") String messageId);

}
