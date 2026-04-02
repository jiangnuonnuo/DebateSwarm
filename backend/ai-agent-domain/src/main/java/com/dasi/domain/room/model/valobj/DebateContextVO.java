package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: xerina
 * @Description: 辩论会话上下文信息 (Value Object - 在单场辩论中保持不变)
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DebateContextVO {

    /** 辩论主题 */
    private String topic;

    /** 正方成员列表 */
    private List<String> proClientIds;

    /** 反方成员列表 */
    private List<String> conClientIds;

    /** 每轮固定对话次数 */
    private Integer turnsPerRound;

}
