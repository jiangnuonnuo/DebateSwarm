package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MeetingStatus {

    PENDING("PENDING", "待开始"),
    IN_PROGRESS("IN_PROGRESS", "进行中"),
    RETURNED("RETURNED", "退回修改"),
    WAIT_DECISION("WAIT_DECISION", "待决策"),
    CONCLUDED("CONCLUDED", "已结论"),
    NEXT_ROUND_PENDING("NEXT_ROUND_PENDING", "下一轮待开"),
    TERMINATED("TERMINATED", "已终止");

    private final String code;

    private final String desc;

    public static MeetingStatus getByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MeetingStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}

