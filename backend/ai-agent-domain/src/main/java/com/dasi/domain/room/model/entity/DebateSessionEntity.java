package com.dasi.domain.room.model.entity;

import com.dasi.domain.room.model.valobj.DebateStatus;
import com.dasi.domain.room.model.valobj.DebateRoundSummaryVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: xerina
 * @Description: 辩论会话领域实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DebateSessionEntity {
    /** 辩论会话业务ID (debate_xxx) */
    private String sessionId;

    /** 关联房间ID */
    private String roomId;

    /** 辩论主题 */
    private String topic;

    /** 仲裁者(主持人) CLIENT ID */
    private String arbitratorClientId;

    /** 正方CLIENT ID列表 */
    private List<String> proClientIds;

    /** 反方CLIENT ID列表 */
    private List<String> conClientIds;

    /** 每轮固定对话次数 */
    private Integer turnsPerRound;

    /** 当前轮次号 */
    private Integer currentRound;

    /** 当前轮内对话序号 */
    private Integer currentTurn;

    /** 每轮胜方记录 (PRO,CON,...) */
    private List<String> roundWinners;

    /** 辩论状态: PENDING/RUNNING/ROUND_END/FINISHED */
    private DebateStatus status;

    /** 乐观锁版本号 */
    private Integer version;

    public void startFirstRound() {
        this.currentRound = 1;
        this.currentTurn = 0;
        this.status = DebateStatus.RUNNING;
        if (this.roundWinners == null) {
            this.roundWinners = new ArrayList<>();
        }
    }

    public void recordTurnFinished() {
        if (currentTurn == null) {
            currentTurn = 0;
        }
        currentTurn++;
    }

    public void finishCurrentRound() {
        if (!isRunning()) {
            throw new IllegalStateException("当前辩论未在运行中，无法结束本轮");
        }
        this.status = DebateStatus.ROUND_END;
    }

    public boolean canDeclareWinner() {
        return DebateStatus.ROUND_END.equals(this.status);
    }

    public void declareRoundWinner(String winnerSide) {
        if (!canDeclareWinner()) {
            throw new IllegalStateException("当前不处于待宣判状态");
        }
        if (!"PRO".equals(winnerSide) && !"CON".equals(winnerSide)) {
            throw new IllegalArgumentException("winnerSide 只能是 PRO 或 CON");
        }
        if (roundWinners == null) {
            roundWinners = new ArrayList<>();
        }
        if (roundWinners.size() >= currentRound) {
            throw new IllegalStateException("当前轮已宣判胜方");
        }
        roundWinners.add(winnerSide);
    }

    public boolean canStartNextRound() {
        return DebateStatus.ROUND_END.equals(status)
                && roundWinners != null
                && roundWinners.size() >= currentRound;
    }

    public void startNextRound() {
        if (!canStartNextRound()) {
            throw new IllegalStateException("当前轮尚未宣判，无法开始下一轮");
        }
        if (currentRound == null) {
            currentRound = 0;
        }
        currentRound++;
        currentTurn = 0;
        status = DebateStatus.RUNNING;
    }

    public void finishDebate() {
        this.status = DebateStatus.FINISHED;
    }

    public String getSideForClient(String clientId) {
        if (proClientIds != null && proClientIds.contains(clientId)) {
            return "PRO";
        }
        if (conClientIds != null && conClientIds.contains(clientId)) {
            return "CON";
        }
        return null;
    }

    /**
     * 获取所有辩手 ID 列表 (正方 + 反方)
     */
    public List<String> getAllDebaterIds() {
        List<String> all = new ArrayList<>();
        if (proClientIds != null) all.addAll(proClientIds);
        if (conClientIds != null) all.addAll(conClientIds);
        return all;
    }

    public boolean isRoundComplete() {
        return currentTurn != null && turnsPerRound != null && currentTurn >= turnsPerRound;
    }

    public boolean validateDebater(String clientId) {
        return getSideForClient(clientId) != null;
    }

    public String nextSpeakerByRoundRobin(String lastSpeakerId) {
        if (proClientIds == null || proClientIds.isEmpty() || conClientIds == null || conClientIds.isEmpty()) {
            return null;
        }

        if (lastSpeakerId == null || lastSpeakerId.isBlank()) {
            return proClientIds.get(0);
        }

        String lastSide = getSideForClient(lastSpeakerId);
        if ("PRO".equals(lastSide)) {
            return nextFromSide(conClientIds, lastSpeakerId);
        }
        if ("CON".equals(lastSide)) {
            return nextFromSide(proClientIds, lastSpeakerId);
        }

        return proClientIds.get(0);
    }

    public DebateRoundSummaryVO buildRoundSummary(String lastSpeakerId, String lastSpeakerName) {
        return DebateRoundSummaryVO.builder()
                .roundNumber(currentRound)
                .turnCount(currentTurn)
                .lastSpeakerId(lastSpeakerId)
                .lastSpeakerName(lastSpeakerName)
                .waitingForWinner(DebateStatus.ROUND_END.equals(status))
                .build();
    }

    public boolean isRunning() {
        return DebateStatus.RUNNING.equals(this.status);
    }

    public boolean isFinished() {
        return DebateStatus.FINISHED.equals(this.status);
    }

    private String nextFromSide(List<String> sideMembers, String lastSpeakerId) {
        if (sideMembers == null || sideMembers.isEmpty()) {
            return null;
        }
        List<String> members = new ArrayList<>(sideMembers);
        int currentIndex = members.indexOf(lastSpeakerId);
        if (currentIndex < 0) {
            return members.get(0);
        }
        return members.get((currentIndex + 1) % members.size());
    }

    /**
     * 将逗号分隔字符串转换为 List
     */
    public static List<String> strToList(String str) {
        if (str == null || str.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 将 List 转换为逗号分隔字符串
     */
    public static String listToStr(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return String.join(",", list);
    }

    public List<String> safeRoundWinners() {
        return roundWinners == null ? Collections.emptyList() : roundWinners;
    }
}
