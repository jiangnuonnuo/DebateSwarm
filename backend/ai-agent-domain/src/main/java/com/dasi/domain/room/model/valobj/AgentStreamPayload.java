package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.model.valobj
 * @Author: xerina
 * @CreateTime: 2026-03-23  23:05
 * @Description: 智能体流式对话数据负载
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentStreamPayload {

    /** 智能体ID */
    private String agentId;

    /** 智能体名称 */
    private String agentName;

    /** 文本片段内容 */
    private String content;

    /** 是否结束 */
    private boolean isEnd;

}
