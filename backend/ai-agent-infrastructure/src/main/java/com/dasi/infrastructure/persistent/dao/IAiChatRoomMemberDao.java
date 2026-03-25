package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiChatRoomMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Autor：xerina
 * @description：聊天室成员仓储接口
 */
@Mapper
public interface IAiChatRoomMemberDao {

    /**
     * 插入成员 (支持 ON DUPLICATE KEY UPDATE 逻辑由 Mapper 实现)
     */
    void insert(AiChatRoomMember aiChatRoomMember);

    /**
     * 查询房间内的所有成员 (按需查询：member_id, member_type, member_name, agent_session_id)
     */
    List<AiChatRoomMember> queryMemberByRoomId(@Param("roomId") String roomId);

    /**
     * 查询房间内的所有 AI 智能体成员
     */
    List<AiChatRoomMember> queryAgentMemberByRoomId(@Param("roomId") String roomId);

    /**
     * 查询房间内的所有客户端成员
     */
    List<AiChatRoomMember> queryClientMemberByRoomId(@Param("roomId") String roomId);

    /**
     * 根据房间ID和成员ID查询特定成员信息
     */
    AiChatRoomMember queryMemberByRoomIdAndMemberId(@Param("roomId") String roomId, @Param("memberId") String memberId);

    /**
     * 移除成员 (逻辑删除)
     */
    void deleteMember(@Param("roomId") String roomId, @Param("memberId") String memberId);
}
