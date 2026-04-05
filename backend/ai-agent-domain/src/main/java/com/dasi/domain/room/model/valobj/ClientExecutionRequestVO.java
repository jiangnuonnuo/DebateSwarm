package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: xerina
 * @Description: 辩手执行请求
 * 职责：把本次发言执行和其所属的辩论护栏绑定，避免旧回调污染新会话。
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
}
