package com.dasi.domain.room.apapter.repository;

import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.entity.DebateRecordEntity;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.valobj.DebateContextVO;
import com.dasi.domain.room.model.valobj.DebateTurnRecordVO;

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
     * 查询成员参与的所有房间
     */
    List<AiChatRoomEntity> queryRoomsByMemberId(String memberId);

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
     * 查询房间内的所有客户端成员
     */
    List<AiChatRoomMemberEntity> queryClientsByRoomId(String roomId);

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

    /**
     * 游标查询聊天记录
     */
    List<AiChatRoomMessageEntity> queryMessagesByCursor(String roomId, Long cursorTime, Integer limit);

    String queryExtConfigByRoomId(String roomId);

    Boolean queryMemberExistByMemberId(String roomId, String memberId);

    /**
     * 保存/更新辩论会话
     * @param session 辩论会话领域对象
     */
    void saveDebateSession(DebateSessionEntity session);

    /**
     * 查询房间内的活跃辩论会话 (status IN ('RUNNING', 'ROUND_END'))
     * @param roomId 房间ID
     * @return 活跃会话Entity，若无则返回null
     */
    DebateSessionEntity queryActiveDebateSession(String roomId);

    /**
     * 根据业务ID查询辩论会话
     * @param sessionId 会话ID
     * @return 会话Entity
     */
    DebateSessionEntity queryDebateSessionBySessionId(String sessionId);

    /**
     * 乐观锁更新辩论会话
     * @param session 包含业务ID和version的会话对象
     * @return 是否更新成功
     */
    boolean updateDebateSession(DebateSessionEntity session);

    /**
     * 保存辩论对话记录
     * @param record 辩论记录Entity
     */
    void saveDebateRecord(DebateRecordEntity record);

    /**
     * 按轮次查询辩论发言记录
     * @param sessionId 会话ID
     * @param roundNumber 轮次号
     * @return 记录列表
     */
    List<DebateRecordEntity> queryDebateRecords(String sessionId, int roundNumber);

    /**
     * 查询辩论会话上下文信息 (含缓存处理)
     * @param sessionId 会话ID
     * @return 辩论上下文 VO
     */
    DebateContextVO queryDebateContext(String sessionId);

    /**
     * 查询用于组装 Prompt 的辩论轮次记录 (JOIN 查询)
     * @param sessionId 会话ID
     * @param roundNumber 轮次号
     * @return 辩论轮次记录列表 VO
     */
    List<DebateTurnRecordVO> queryDebateRecordsForPrompt(String sessionId, int roundNumber);
}
