package com.dasi.domain.room.model.entity;

import com.dasi.domain.room.model.valobj.DebateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * 获取指定 Client 在辩论中的阵营
     * @param clientId 参与者ID
     * @return PRO/CON/null
     */
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

    /**
     * 检查本轮是否已完成
     */
    public boolean isRoundComplete() {
        return currentTurn != null && turnsPerRound != null && currentTurn >= turnsPerRound;
    }

    /**
     * 推进到下一个发言位
     */
    public void advanceTurn() {
        if (currentTurn == null) currentTurn = 0;
        currentTurn++;
    }

    /**
     * 推进到下一轮
     */
    public void advanceRound() {
        if (currentRound == null) currentRound = 0;
        currentRound++;
        currentTurn = 0;
        status = DebateStatus.RUNNING;
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
}
