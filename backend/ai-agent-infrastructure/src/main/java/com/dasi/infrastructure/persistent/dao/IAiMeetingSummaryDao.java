package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiMeetingSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 会议总结 DAO
 */
@Mapper
public interface IAiMeetingSummaryDao {

    void insert(AiMeetingSummary record);

    AiMeetingSummary queryByMeetingId(@Param("meetingId") String meetingId);

    int updateByMeetingId(AiMeetingSummary record);
}

