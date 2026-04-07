package com.dasi.domain.room.service.debate.arbitration.strategy;

import com.dasi.domain.room.model.valobj.ArbitrationDecisionResultVO;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;
import com.dasi.domain.room.model.valobj.DebateMemberStatusVO;
import com.dasi.domain.room.model.valobj.DebateTurnRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author: xerina
 * @Description: 仲裁降级轮转策略
 */
@Service
@Slf4j
public class RoundRobinFallbackDecisionStrategy implements IArbitrationDecisionStrategy {

    private static final String STRATEGY_TYPE = "ROUND_ROBIN_FALLBACK";

    @Override
    public ArbitrationDecisionResultVO decide(ArbitrationPromptContextVO promptContext) {
        String speakerId = pickNextSpeaker(promptContext);
        if (speakerId == null && promptContext.getCandidateSpeakerIds() != null && !promptContext.getCandidateSpeakerIds().isEmpty()) {
            speakerId = promptContext.getCandidateSpeakerIds().get(0);
        }
        log.info("【ARBITRATOR_FALLBACK_PICK】sessionId={}, roomId={}, speakerId={}, requiredSide={}, candidates={}, preferred={}",
                promptContext.getSessionId(),
                promptContext.getRoomId(),
                speakerId,
                promptContext.getRequiredSide(),
                promptContext.getCandidateSpeakerIds(),
                promptContext.getPreferredSpeakerIds());
        return ArbitrationDecisionResultVO.builder()
                .speakerId(speakerId)
                .reasoning(buildFallbackReasoning(promptContext, speakerId))
                .decisionSource(strategyType())
                .build();
    }

    @Override
    public String strategyType() {
        return STRATEGY_TYPE;
    }

    private String pickNextSpeaker(ArbitrationPromptContextVO promptContext) {
        if (promptContext.getPreferredSpeakerIds() != null && !promptContext.getPreferredSpeakerIds().isEmpty()) {
            return promptContext.getPreferredSpeakerIds().get(0);
        }

        List<DebateTurnRecordVO> roundHistory = promptContext.getRoundHistory();
        if (roundHistory == null || roundHistory.isEmpty()) {
            return firstClientId(promptContext.getProMembers());
        }

        DebateTurnRecordVO latestRecord = roundHistory.get(roundHistory.size() - 1);
        String targetSide = "PRO".equals(latestRecord.getSide()) ? "CON" : "PRO";
        List<DebateMemberStatusVO> targetMembers = "PRO".equals(targetSide) ? promptContext.getProMembers() : promptContext.getConMembers();
        String latestSpeakerOnTargetSide = findLatestSpeakerOnSide(roundHistory, targetSide);
        String speakerId = nextMemberId(targetMembers, latestSpeakerOnTargetSide);
        if (speakerId != null && promptContext.getCandidateSpeakerIds().contains(speakerId)) {
            return speakerId;
        }

        List<DebateMemberStatusVO> oppositeMembers = "PRO".equals(targetSide) ? promptContext.getConMembers() : promptContext.getProMembers();
        String oppositeSpeakerId = nextMemberId(oppositeMembers, findLatestSpeakerOnSide(roundHistory, "PRO".equals(targetSide) ? "CON" : "PRO"));
        if (oppositeSpeakerId != null && promptContext.getCandidateSpeakerIds().contains(oppositeSpeakerId)) {
            return oppositeSpeakerId;
        }
        return null;
    }

    private String findLatestSpeakerOnSide(List<DebateTurnRecordVO> roundHistory, String side) {
        for (int i = roundHistory.size() - 1; i >= 0; i--) {
            DebateTurnRecordVO record = roundHistory.get(i);
            if (record != null && side.equals(record.getSide())) {
                return record.getSpeakerId();
            }
        }
        return null;
    }

    private String nextMemberId(List<DebateMemberStatusVO> members, String lastSpeakerId) {
        if (members == null || members.isEmpty()) {
            return null;
        }
        if (lastSpeakerId == null || lastSpeakerId.isBlank()) {
            return members.get(0).getClientId();
        }
        for (int i = 0; i < members.size(); i++) {
            if (lastSpeakerId.equals(members.get(i).getClientId())) {
                return members.get((i + 1) % members.size()).getClientId();
            }
        }
        return members.get(0).getClientId();
    }

    private String firstClientId(List<DebateMemberStatusVO> members) {
        return (members == null || members.isEmpty()) ? null : members.get(0).getClientId();
    }

    private String buildFallbackReasoning(ArbitrationPromptContextVO promptContext, String speakerId) {
        if (speakerId == null) {
            return "本轮暂无可继续发言的辩手。";
        }
        return "本次按轮转补位继续，请该辩手围绕上一条核心论点回应。";
    }
}
