package com.dasi.domain.room.apapter.repository;

import com.dasi.domain.room.model.entity.MeetingActionEntity;
import com.dasi.domain.room.model.entity.MeetingIssueEntity;
import com.dasi.domain.room.model.entity.MeetingSessionEntity;
import com.dasi.domain.room.model.valobj.MeetingSummaryVO;

import java.util.List;

public interface IMeetingRepository {

    void saveMeeting(MeetingSessionEntity meeting);

    MeetingSessionEntity queryMeetingById(String meetingId);

    MeetingSessionEntity queryActiveMeetingByRoomId(String roomId);

    boolean updateMeetingWithVersion(MeetingSessionEntity meeting, Integer expectedVersion);

    boolean saveAction(MeetingActionEntity action);

    MeetingActionEntity queryActionByClientRequestId(String meetingId, String clientRequestId);

    List<MeetingActionEntity> queryActions(String meetingId);

    void saveIssue(MeetingIssueEntity issue);

    List<MeetingIssueEntity> queryIssues(String meetingId);

    void resolveOpenIssues(String meetingId, String assigneeRole);

    void saveSummary(String meetingId, MeetingSummaryVO summary);

    MeetingSummaryVO querySummary(String meetingId);
}
