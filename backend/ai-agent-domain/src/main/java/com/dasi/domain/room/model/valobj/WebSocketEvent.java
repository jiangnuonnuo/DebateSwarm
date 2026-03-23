package com.dasi.domain.room.model.valobj;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.model.valobj
 * @Author: xerina
 * @CreateTime: 2026-03-23  18:20
 * @Description: TODO
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Autor：xerina
 * @description：WebSocket 统一传输信封类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WebSocketEvent<T> {
    /** 房间ID (必填，前端路由依据) */
    private String roomId;

    /**
     * 事件类型 (枚举字符串)
     * 例如：AGENT_STREAM (AI流式输出), ARBITER_NOTICE (仲裁者通知),
     *      STATE_UPDATE (房间状态变更), ERROR (异常提醒)
     */
    private String eventType;

    /** 追踪ID (可选，用于将多个数据包关联到同一个原始请求) */
    private String traceId;

    /** 毫秒时间戳 */
    private Long timestamp;

    /**
     * 数据负载 (多态)
     * 根据 eventType 的不同，这里可以是不同的具体对象
     */
    private T payload;
}
