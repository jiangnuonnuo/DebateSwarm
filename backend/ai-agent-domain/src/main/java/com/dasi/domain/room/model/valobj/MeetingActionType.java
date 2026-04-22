package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MeetingActionType {

    START("START", "开始会议"),
    REPORT("REPORT", "汇报"),
    RETURN("RETURN", "退回"),
    APPROVE("APPROVE", "通过"),
    QUESTION("QUESTION", "补充提问"),
    ASSIGN("ASSIGN", "指派执行"),
    DECISION("DECISION", "进入决策"),
    RESUME("RESUME", "恢复流转"),
    CONCLUDE("CONCLUDE", "形成结论"),
    TERMINATE("TERMINATE", "终止会议");

    private final String code;

    private final String desc;

    public static MeetingActionType getByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MeetingActionType value : values()) {
            if (value.getCode().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}

