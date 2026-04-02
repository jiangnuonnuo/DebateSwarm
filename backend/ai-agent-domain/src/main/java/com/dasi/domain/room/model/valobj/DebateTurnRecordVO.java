package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @Author: xerina
 * @Description: 辩论单回合记录信息 (Value Object - 用于 Prompt 上下文组装)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DebateTurnRecordVO {

    /** 发言者阵营 (PRO/CON) */
    private String side;

    /** 发言者 ID */
    private String speakerId;

    /** 发言者名称 */
    private String speakerName;

    /** 发言内容 */
    private String content;

}
