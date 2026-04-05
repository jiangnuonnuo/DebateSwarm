package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: xerina
 * @Description: Client 执行请求
 * 职责：统一描述一次 client 调用所需的上下文；辩论模式会绑定护栏，自由聊天 Multi-At 会附带批次信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientExecutionRequestVO {

    /** 房间ID */
    private String roomId;

    /** 本次应发言的 clientId */
    private String clientId;

    /** 所属辩论会话ID */
    private String sessionId;

    /** 所属轮次 */
    private Integer roundNumber;

    /** 调度时的会话版本 */
    private Integer sessionVersion;

    /** 本次执行链路唯一ID */
    private String dispatchTraceId;

    /** 决策来源，仅用于日志与落库描述 */
    private String decisionSource;

    /** 仲裁说明，仅用于日志与审计 */
    private String reasoning;

    /** Multi-At 批次追踪ID */
    private String batchTraceId;

    /** Multi-At 原始输入顺序 */
    private Integer orderIndex;

    /** 本次批量 @ 的目标成员列表，仅用于日志与 extData 审计 */
    private List<String> requestedAtMemberIds;
}
