package com.dasi.domain.room.service.context;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;
import org.springframework.stereotype.Component;

/**
 * @Author: xerina
 * @Description: 仲裁动态指令装配器（运行时，不入库）
 */
@Component
public class DebateInstructionAssembler {

    public String buildInstruction(ArbitrationPromptContextVO promptContext) {
        if (promptContext == null) {
            return "当前无可用仲裁上下文，请从候选集中返回一个合法 speakerId。";
        }

        String requiredSide = safe(promptContext.getRequiredSide());
        String stageHint = requiredSide.isBlank()
                ? "当前是首发/自由槽位，请在候选集中选择最适合推进攻防的辩手。"
                : "当前为失败补位槽位，必须优先选择阵营 " + requiredSide + " 的候选辩手。";

        return """
                [仲裁运行时动态指令]
                roomId=%s
                sessionId=%s
                round=%s
                turn=%s/%s
                lastSpeakerId=%s
                lastSpeakerSide=%s
                requiredSide=%s
                slotRetryCount=%s
                candidateSpeakerIds=%s
                preferredSpeakerIds=%s
                excludedSpeakerIds=%s
                speakerHistoryStats=%s

                执行要求：
                1. speakerId 只能从 candidateSpeakerIds 中选择；
                2. 若 requiredSide 非空，必须满足该阵营约束；
                3. 优先参考 preferredSpeakerIds 的顺序；
                4. reasoning 一句话，说明本次选择如何推进当前攻防。

                %s
                """.formatted(
                safe(promptContext.getRoomId()),
                safe(promptContext.getSessionId()),
                safeNumber(promptContext.getCurrentRound()),
                safeNumber(promptContext.getCurrentTurn()),
                safeNumber(promptContext.getTurnsPerRound()),
                safe(promptContext.getLastSpeakerClientId()),
                safe(promptContext.getLastSpeakerSide()),
                requiredSide,
                safeNumber(promptContext.getSlotRetryCount()),
                safeJson(promptContext.getCandidateSpeakerIds()),
                safeJson(promptContext.getPreferredSpeakerIds()),
                safeJson(promptContext.getExcludedSpeakerIds()),
                safeJson(promptContext.getSpeakerHistoryStats()),
                stageHint
        );
    }

    public String buildDigest(String instruction) {
        if (instruction == null) {
            return "len=0,hash=0";
        }
        return "len=" + instruction.length() + ",hash=" + Integer.toHexString(instruction.hashCode());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeNumber(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private String safeJson(Object value) {
        if (value == null) {
            return "[]";
        }
        return JSON.toJSONString(value);
    }
}
