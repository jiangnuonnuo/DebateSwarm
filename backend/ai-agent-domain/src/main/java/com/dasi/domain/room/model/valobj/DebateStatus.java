package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author: xerina
 * @Description: 辩论赛状态枚举
 */
@Getter
@AllArgsConstructor
public enum DebateStatus {
    PENDING("PENDING", "待开始"),
    RUNNING("RUNNING", "进行中"),
    ROUND_END("ROUND_END", "本轮结束"),
    FINISHED("FINISHED", "已结束");

    private final String code;
    private final String desc;

    public static DebateStatus getByCode(String code) {
        for (DebateStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
