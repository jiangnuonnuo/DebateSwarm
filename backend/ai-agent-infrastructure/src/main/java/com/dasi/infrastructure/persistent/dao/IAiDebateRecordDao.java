package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiDebateRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @Author: xerina
 * @Description: 辩论对话记录表 DAO
 */
@Mapper
public interface IAiDebateRecordDao {

    /**
     * 按会话ID和轮次查询记录 (JOIN 消息表获取发言内容)
     */
    List<AiDebateRecord> queryRecordsForPrompt(@Param("sessionId") String sessionId, @Param("roundNumber") Integer roundNumber);

    /**
     * 插入对话记录
     */
    void insert(AiDebateRecord record);

    /**
     * 按会话ID和轮次查询记录
     */
    List<AiDebateRecord> queryBySessionAndRound(@Param("sessionId") String sessionId, @Param("roundNumber") Integer roundNumber);

    List<AiDebateRecord> queryLatestBySessionId(@Param("sessionId") String sessionId, @Param("limit") Integer limit);

    /**
     * 物理/逻辑删除会话相关记录
     */
    void deleteBySessionId(@Param("sessionId") String sessionId);
}
