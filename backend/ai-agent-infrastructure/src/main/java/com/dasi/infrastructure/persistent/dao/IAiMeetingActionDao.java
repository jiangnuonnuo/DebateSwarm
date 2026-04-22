package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiMeetingAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会议动作流水 DAO
 */
@Mapper
public interface IAiMeetingActionDao {

    void insert(AiMeetingAction record);

    AiMeetingAction queryByClientRequestId(@Param("meetingId") String meetingId,
                                           @Param("clientRequestId") String clientRequestId);

    List<AiMeetingAction> queryTimelineByMeetingId(@Param("meetingId") String meetingId,
                                                   @Param("limit") Integer limit);
}

