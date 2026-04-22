package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiMeetingSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会议主表 DAO
 */
@Mapper
public interface IAiMeetingSessionDao {

    void insert(AiMeetingSession record);

    AiMeetingSession queryByMeetingId(@Param("meetingId") String meetingId);

    AiMeetingSession queryActiveByRoomId(@Param("roomId") String roomId);

    int updateLifecycleWithLock(AiMeetingSession record);

    int updateMeetingSnapshot(AiMeetingSession record);
}

