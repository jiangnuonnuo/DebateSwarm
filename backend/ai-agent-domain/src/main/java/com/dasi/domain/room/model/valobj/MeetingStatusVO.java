package com.dasi.domain.room.model.valobj;

import com.dasi.domain.room.model.entity.MeetingIssueEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingStatusVO {

    private String meetingId;

    private String roomId;

    private String topic;

    private String goal;

    private MeetingStatus status;

    private Integer roundNumber;

    private Integer version;

    private String ownerId;

    private String ownerRole;

    private String pmId;

    private Map<String, String> roleAssignments;

    private MeetingSummaryVO summary;

    private List<MeetingIssueEntity> openIssues;

    private Integer timelineSize;
}

