package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: xerina
 * @Description: Client 回复生成结果
 * 职责：承接 Multi-At 的异步生成结果，拆分“生成回复”和“推送消息”两个阶段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientReplyResultVO {

    /** 房间ID */
    private String roomId;

    /** 执行的 clientId */
    private String clientId;

    /** 展示名称 */
    private String clientName;

    /** 生成的回复内容 */
    private String content;

    /** 是否生成成功 */
    private boolean success;

    /** 失败类型 */
    private String errorType;

    /** 失败说明 */
    private String errorMessage;

    /** Multi-At 批次追踪ID */
    private String batchTraceId;

    /** Multi-At 原始输入顺序 */
    private Integer orderIndex;

    /** 本次批量 @ 的目标成员列表 */
    private List<String> requestedAtMemberIds;

    /** 开始时间戳 */
    private Long startedAt;

    /** 结束时间戳 */
    private Long finishedAt;
}
