package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiDebateSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @Author: xerina
 * @Description: 辩论会话表 DAO
 */
@Mapper
public interface IAiDebateSessionDao {

    /**
     * 插入新会话
     */
    void insert(AiDebateSession session);

    /**
     * 根据业务ID查询会话
     */
    AiDebateSession querySessionDetailBySessionId(@Param("sessionId") String sessionId);

    /**
     * 根据房间ID查询活跃辩论会话头信息
     */
    AiDebateSession queryActiveSessionHeaderByRoomId(@Param("roomId") String roomId);

    /**
     * 查询会话上下文信息 (中频查询，包含主题、正反方成员等不变量)
     */
    AiDebateSession querySessionContext(@Param("sessionId") String sessionId);

    /**
     * 乐观锁更新会话状态或进度
     * @return 影响行数
     */
    int updateWithLock(AiDebateSession session);

    /**
     * 强制更新会话状态（不校验 version）
     */
    int updateStatusForce(AiDebateSession session);

    /**
     * 逻辑删除会话
     */
    void deleteBySessionId(@Param("sessionId") String sessionId);
}
