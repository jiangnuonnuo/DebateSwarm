package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiMeetingIssue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会议问题单 DAO
 */
@Mapper
public interface IAiMeetingIssueDao {

    void insert(AiMeetingIssue record);

    List<AiMeetingIssue> queryByMeetingId(@Param("meetingId") String meetingId);

    int resolveOpenIssuesByRole(@Param("meetingId") String meetingId,
                                @Param("assigneeRole") String assigneeRole,
                                @Param("resolvedAt") LocalDateTime resolvedAt);

    int resolveAllOpenIssues(@Param("meetingId") String meetingId,
                             @Param("resolvedAt") LocalDateTime resolvedAt);
}

