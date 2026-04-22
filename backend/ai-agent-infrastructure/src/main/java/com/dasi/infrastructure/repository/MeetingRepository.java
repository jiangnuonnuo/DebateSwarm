package com.dasi.infrastructure.repository;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.dasi.domain.room.apapter.repository.IMeetingRepository;
import com.dasi.domain.room.model.entity.MeetingActionEntity;
import com.dasi.domain.room.model.entity.MeetingIssueEntity;
import com.dasi.domain.room.model.entity.MeetingSessionEntity;
import com.dasi.domain.room.model.valobj.MeetingActionType;
import com.dasi.domain.room.model.valobj.MeetingStatus;
import com.dasi.domain.room.model.valobj.MeetingSummaryVO;
import com.dasi.infrastructure.persistent.dao.IAiMeetingActionDao;
import com.dasi.infrastructure.persistent.dao.IAiMeetingIssueDao;
import com.dasi.infrastructure.persistent.dao.IAiMeetingSessionDao;
import com.dasi.infrastructure.persistent.dao.IAiMeetingSummaryDao;
import com.dasi.infrastructure.persistent.po.AiMeetingAction;
import com.dasi.infrastructure.persistent.po.AiMeetingIssue;
import com.dasi.infrastructure.persistent.po.AiMeetingSession;
import com.dasi.infrastructure.persistent.po.AiMeetingSummary;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会议治理仓储实现。
 * 该实现只负责数据库持久化与对象转换，不承载业务状态机逻辑。
 */
@Repository
public class MeetingRepository implements IMeetingRepository {

    @Resource
    private IAiMeetingSessionDao aiMeetingSessionDao;

    @Resource
    private IAiMeetingActionDao aiMeetingActionDao;

    @Resource
    private IAiMeetingIssueDao aiMeetingIssueDao;

    @Resource
    private IAiMeetingSummaryDao aiMeetingSummaryDao;

    @Override
    public void saveMeeting(MeetingSessionEntity meeting) {
        AiMeetingSession dbRecord = toMeetingPo(meeting);
        AiMeetingSession existed = aiMeetingSessionDao.queryByMeetingId(meeting.getMeetingId());
        if (existed == null) {
            aiMeetingSessionDao.insert(dbRecord);
            return;
        }
        aiMeetingSessionDao.updateMeetingSnapshot(dbRecord);
    }

    @Override
    public MeetingSessionEntity queryMeetingById(String meetingId) {
        AiMeetingSession dbRecord = aiMeetingSessionDao.queryByMeetingId(meetingId);
        return toMeetingEntity(dbRecord);
    }

    @Override
    public MeetingSessionEntity queryActiveMeetingByRoomId(String roomId) {
        AiMeetingSession dbRecord = aiMeetingSessionDao.queryActiveByRoomId(roomId);
        return toMeetingEntity(dbRecord);
    }

    @Override
    public boolean updateMeetingWithVersion(MeetingSessionEntity meeting, Integer expectedVersion) {
        AiMeetingSession dbRecord = toMeetingPo(meeting);
        dbRecord.setVersion(expectedVersion);
        int changedRows = aiMeetingSessionDao.updateLifecycleWithLock(dbRecord);
        return changedRows > 0;
    }

    @Override
    public boolean saveAction(MeetingActionEntity action) {
        AiMeetingAction dbRecord = toActionPo(action);
        try {
            aiMeetingActionDao.insert(dbRecord);
            return true;
        } catch (DuplicateKeyException duplicateKeyException) {
            return false;
        }
    }

    @Override
    public MeetingActionEntity queryActionByClientRequestId(String meetingId, String clientRequestId) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return null;
        }
        AiMeetingAction dbRecord = aiMeetingActionDao.queryByClientRequestId(meetingId, clientRequestId);
        return toActionEntity(dbRecord);
    }

    @Override
    public List<MeetingActionEntity> queryActions(String meetingId) {
        List<AiMeetingAction> dbRecords = aiMeetingActionDao.queryTimelineByMeetingId(meetingId, 200);
        return toActionEntityList(dbRecords);
    }

    @Override
    public void saveIssue(MeetingIssueEntity issue) {
        aiMeetingIssueDao.insert(toIssuePo(issue));
    }

    @Override
    public List<MeetingIssueEntity> queryIssues(String meetingId) {
        List<AiMeetingIssue> dbRecords = aiMeetingIssueDao.queryByMeetingId(meetingId);
        return toIssueEntityList(dbRecords);
    }

    @Override
    public void resolveOpenIssues(String meetingId, String assigneeRole) {
        if (assigneeRole == null || assigneeRole.isBlank()) {
            aiMeetingIssueDao.resolveAllOpenIssues(meetingId, LocalDateTime.now());
            return;
        }
        aiMeetingIssueDao.resolveOpenIssuesByRole(meetingId, assigneeRole, LocalDateTime.now());
    }

    @Override
    public void saveSummary(String meetingId, MeetingSummaryVO summary) {
        AiMeetingSummary dbRecord = toSummaryPo(meetingId, summary);
        AiMeetingSummary existed = aiMeetingSummaryDao.queryByMeetingId(meetingId);
        if (existed == null) {
            aiMeetingSummaryDao.insert(dbRecord);
            return;
        }
        aiMeetingSummaryDao.updateByMeetingId(dbRecord);
    }

    @Override
    public MeetingSummaryVO querySummary(String meetingId) {
        AiMeetingSummary dbRecord = aiMeetingSummaryDao.queryByMeetingId(meetingId);
        return toSummaryVo(dbRecord);
    }

    /**
     * 会议实体 -> 持久化对象
     */
    private AiMeetingSession toMeetingPo(MeetingSessionEntity source) {
        if (source == null) {
            return null;
        }
        return AiMeetingSession.builder()
                .meetingId(source.getMeetingId())
                .roomId(source.getRoomId())
                .topic(source.getTopic())
                .goal(source.getGoal())
                .status(source.getStatus() == null ? null : source.getStatus().getCode())
                .roundNumber(source.getRoundNumber())
                .ownerId(source.getOwnerId())
                .ownerRole(source.getOwnerRole())
                .pmId(source.getPmId())
                .roleAssignmentsJson(toRoleAssignmentsJson(source.getRoleAssignments()))
                .version(source.getVersion())
                .build();
    }

    /**
     * 持久化对象 -> 会议实体
     */
    private MeetingSessionEntity toMeetingEntity(AiMeetingSession source) {
        if (source == null) {
            return null;
        }
        return MeetingSessionEntity.builder()
                .meetingId(source.getMeetingId())
                .roomId(source.getRoomId())
                .topic(source.getTopic())
                .goal(source.getGoal())
                .status(MeetingStatus.getByCode(source.getStatus()))
                .roundNumber(source.getRoundNumber())
                .ownerId(source.getOwnerId())
                .ownerRole(source.getOwnerRole())
                .pmId(source.getPmId())
                .roleAssignments(toRoleAssignments(source.getRoleAssignmentsJson()))
                .version(source.getVersion())
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime())
                .build();
    }

    /**
     * 动作实体 -> 持久化对象
     */
    private AiMeetingAction toActionPo(MeetingActionEntity source) {
        if (source == null) {
            return null;
        }
        return AiMeetingAction.builder()
                .actionId(source.getActionId())
                .meetingId(source.getMeetingId())
                .roomId(source.getRoomId())
                .actionType(source.getActionType() == null ? null : source.getActionType().getCode())
                .actorId(source.getActorId())
                .actorRole(source.getActorRole())
                .targetId(source.getTargetId())
                .targetRole(source.getTargetRole())
                .payload(source.getPayload())
                .reason(source.getReason())
                .requiredFix(source.getRequiredFix())
                .deadline(source.getDeadline())
                .result(source.getResult())
                .meetingVersion(source.getMeetingVersion())
                .clientRequestId(source.getClientRequestId())
                .traceId(source.getTraceId())
                .build();
    }

    /**
     * 持久化对象 -> 动作实体
     */
    private MeetingActionEntity toActionEntity(AiMeetingAction source) {
        if (source == null) {
            return null;
        }
        return MeetingActionEntity.builder()
                .actionId(source.getActionId())
                .meetingId(source.getMeetingId())
                .roomId(source.getRoomId())
                .actionType(MeetingActionType.getByCode(source.getActionType()))
                .actorId(source.getActorId())
                .actorRole(source.getActorRole())
                .targetId(source.getTargetId())
                .targetRole(source.getTargetRole())
                .payload(source.getPayload())
                .reason(source.getReason())
                .requiredFix(source.getRequiredFix())
                .deadline(source.getDeadline())
                .result(source.getResult())
                .meetingVersion(source.getMeetingVersion())
                .clientRequestId(source.getClientRequestId())
                .traceId(source.getTraceId())
                .createdAt(source.getCreateTime())
                .build();
    }

    /**
     * 问题单实体 -> 持久化对象
     */
    private AiMeetingIssue toIssuePo(MeetingIssueEntity source) {
        if (source == null) {
            return null;
        }
        return AiMeetingIssue.builder()
                .issueId(source.getIssueId())
                .meetingId(source.getMeetingId())
                .roomId(source.getRoomId())
                .reporterRole(source.getReporterRole())
                .assigneeRole(source.getAssigneeRole())
                .reason(source.getReason())
                .requiredFix(source.getRequiredFix())
                .dueAt(source.getDueAt())
                .status(source.getStatus())
                .resolvedAt(source.getResolvedAt())
                .build();
    }

    /**
     * 持久化对象 -> 问题单实体
     */
    private MeetingIssueEntity toIssueEntity(AiMeetingIssue source) {
        if (source == null) {
            return null;
        }
        return MeetingIssueEntity.builder()
                .issueId(source.getIssueId())
                .meetingId(source.getMeetingId())
                .roomId(source.getRoomId())
                .reporterRole(source.getReporterRole())
                .assigneeRole(source.getAssigneeRole())
                .reason(source.getReason())
                .requiredFix(source.getRequiredFix())
                .dueAt(source.getDueAt())
                .status(source.getStatus())
                .createdAt(source.getCreateTime())
                .resolvedAt(source.getResolvedAt())
                .build();
    }

    private List<MeetingActionEntity> toActionEntityList(List<AiMeetingAction> dbRecords) {
        if (dbRecords == null || dbRecords.isEmpty()) {
            return new ArrayList<>();
        }
        List<MeetingActionEntity> entities = new ArrayList<>(dbRecords.size());
        for (AiMeetingAction dbRecord : dbRecords) {
            entities.add(toActionEntity(dbRecord));
        }
        return entities;
    }

    private List<MeetingIssueEntity> toIssueEntityList(List<AiMeetingIssue> dbRecords) {
        if (dbRecords == null || dbRecords.isEmpty()) {
            return new ArrayList<>();
        }
        List<MeetingIssueEntity> entities = new ArrayList<>(dbRecords.size());
        for (AiMeetingIssue dbRecord : dbRecords) {
            entities.add(toIssueEntity(dbRecord));
        }
        return entities;
    }

    private AiMeetingSummary toSummaryPo(String meetingId, MeetingSummaryVO source) {
        if (source == null) {
            return null;
        }
        return AiMeetingSummary.builder()
                .meetingId(meetingId)
                .conclusion(source.getConclusion())
                .actionItemsJson(source.getActionItems() == null ? "[]" : JSON.toJSONString(source.getActionItems()))
                .nextRoundCondition(source.getNextRoundCondition())
                .concludedByRole(source.getConcludedByRole())
                .concludedAt(source.getConcludedAt())
                .build();
    }

    private MeetingSummaryVO toSummaryVo(AiMeetingSummary source) {
        if (source == null) {
            return null;
        }
        return MeetingSummaryVO.builder()
                .conclusion(source.getConclusion())
                .actionItems(source.getActionItemsJson() == null
                        ? new ArrayList<>()
                        : JSON.parseObject(source.getActionItemsJson(), new TypeReference<List<String>>() {
                        }))
                .nextRoundCondition(source.getNextRoundCondition())
                .concludedByRole(source.getConcludedByRole())
                .concludedAt(source.getConcludedAt())
                .build();
    }

    private String toRoleAssignmentsJson(Map<String, String> roleAssignments) {
        if (roleAssignments == null || roleAssignments.isEmpty()) {
            return "{}";
        }
        return JSON.toJSONString(roleAssignments);
    }

    private Map<String, String> toRoleAssignments(String roleAssignmentsJson) {
        if (roleAssignmentsJson == null || roleAssignmentsJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        return JSON.parseObject(roleAssignmentsJson, new TypeReference<Map<String, String>>() {
        });
    }
}

