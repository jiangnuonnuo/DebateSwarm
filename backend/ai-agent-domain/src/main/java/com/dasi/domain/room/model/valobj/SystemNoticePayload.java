package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description 系统通知事件的负载，用于向前端广播系统级消息
 * @author xerina
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemNoticePayload {
    /** 通知类型，如 ARBITRATION_STATUS_CHANGE */
    private String noticeType;
    /** 通知内容文本 */
    private String content;
}
