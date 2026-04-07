package com.dasi.domain.room.service.context;

import com.dasi.domain.room.model.valobj.RoomRuntimePromptContextVO;
import org.springframework.stereotype.Component;

/**
 * @Author: xerina
 * @Description: 普通聊天运行时头部装配器
 */
@Component
public class RoomRuntimeHeaderAssembler {

    private static final String RUNTIME_HEADER_TEMPLATE =
            "# 群聊运行时指令 (Runtime Prompt Header)\n" +
            "---\n" +
            "## 1. 房间上下文\n" +
            "- roomId: %s\n" +
            "- 房间名称: 【%s】\n" +
            "- 房间描述: %s\n\n" +
            "## 2. 本次执行上下文\n" +
            "- sessionId: %s\n" +
            "- stage: %s\n" +
            "- traceId: %s\n" +
            "- eventType: %s\n" +
            "- sender: %s(%s)\n" +
            "- atMemberIds: %s\n\n" +
            "## 3. 你的身份\n" +
            "- 展示名称: 【%s】\n" +
            "- 唯一标识: %s\n" +
            "- 历史中标注为 [%s(%s) (你)] 的内容，代表你之前的发言。\n\n" +
            "## 4. 执行规则\n" +
            "- 你需要结合房间场景和对话历史自然回复。\n" +
            "- 严禁输出 [名称(ID)] 这类前缀。\n" +
            "- 保持语言简洁，优先回应最近上下文。\n" +
            "- runtimeHint: %s\n\n" +
            "## 5. 对话历史\n" +
            "---";

    public String build(String roomId,
                        String roomName,
                        String roomDesc,
                        String agentId,
                        String currentMemberName,
                        RoomRuntimePromptContextVO runtimePromptContext) {
        String sessionId = runtimePromptContext == null ? "" : safe(runtimePromptContext.getSessionId());
        String stage = runtimePromptContext == null ? "FREE_CHAT" : defaultText(runtimePromptContext.getStage(), "FREE_CHAT");
        String traceId = runtimePromptContext == null ? "" : safe(runtimePromptContext.getTraceId());
        String eventType = runtimePromptContext == null ? "USER_MSG" : defaultText(runtimePromptContext.getEventType(), "USER_MSG");
        String senderId = runtimePromptContext == null ? "" : safe(runtimePromptContext.getSenderId());
        String senderName = runtimePromptContext == null ? "" : safe(runtimePromptContext.getSenderName());
        String atMemberIds = runtimePromptContext == null ? "[]" : defaultText(runtimePromptContext.getAtMemberIds(), "[]");
        String runtimeHint = runtimePromptContext == null
                ? "当前为自由聊天场景。"
                : defaultText(runtimePromptContext.getRuntimeHint(), "请结合最近上下文自然回复。");

        return String.format(
                RUNTIME_HEADER_TEMPLATE,
                safe(roomId),
                safe(roomName),
                safe(roomDesc),
                sessionId,
                stage,
                traceId,
                eventType,
                senderName,
                senderId,
                atMemberIds,
                safe(currentMemberName),
                safe(agentId),
                safe(currentMemberName),
                safe(agentId),
                runtimeHint
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String defaultText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
