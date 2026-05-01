package com.dasi.domain.xhspublish.service.execution.fallback;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class XhsPublishAgentFallbackResultParser {

    public XhsPublishRemoteResult parse(String rawResponse) {
        String normalized = normalize(rawResponse);
        if ("success".equalsIgnoreCase(normalized)) {
            JSONObject result = new JSONObject();
            result.put("status", "published");
            result.put("executor", "agent_fallback");
            return XhsPublishRemoteResult.builder()
                    .status("published")
                    .resultJson(JSON.toJSONString(result))
                    .executor("agent_fallback")
                    .build();
        }

        JSONObject object = parseObject(normalized);
        String status = resolveStatus(object);
        boolean failed = "failed".equalsIgnoreCase(status);
        String errorCode = failed ? defaultText(object.getString("error_code"), "AGENT_FALLBACK_FAILED") : null;
        String errorType = failed ? resolveErrorType(object.getString("error_type"), errorCode, object.getString("error_message")) : null;
        String errorMessage = failed ? sanitizeMessage(defaultText(object.getString("error_message"), "Agent 发布兜底失败")) : null;

        JSONObject view = new JSONObject();
        view.put("status", status);
        view.put("executor", "agent_fallback");
        copyIfPresent(object, view, "job_id");
        copyIfPresent(object, view, "accepted");
        copyIfPresent(object, view, "note_id");
        copyIfPresent(object, view, "note_url");
        copyIfPresent(object, view, "publish_time");
        if (StringUtils.hasText(errorCode)) {
            view.put("error_code", errorCode);
        }
        if (StringUtils.hasText(errorType)) {
            view.put("error_type", errorType);
        }
        if (StringUtils.hasText(errorMessage)) {
            view.put("error_message", errorMessage);
        }

        return XhsPublishRemoteResult.builder()
                .status(status)
                .resultJson(JSON.toJSONString(view))
                .errorCode(errorCode)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .executor("agent_fallback")
                .build();
    }

    private JSONObject parseObject(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new WorkException("Agent 发布兜底结果为空");
        }
        String candidate = rawResponse;
        int firstBrace = candidate.indexOf('{');
        int lastBrace = candidate.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            candidate = candidate.substring(firstBrace, lastBrace + 1);
        }
        JSONObject object = JSON.parseObject(candidate);
        if (object == null || object.isEmpty()) {
            throw new WorkException("Agent 发布兜底结果不是合法 JSON");
        }
        return object;
    }

    private String normalize(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            return "";
        }
        String normalized = rawResponse.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceAll("^```[a-zA-Z]*", "")
                    .replaceAll("```$", "")
                    .trim();
        }
        return normalized;
    }

    private String resolveStatus(JSONObject object) {
        String status = object.getString("status");
        if (StringUtils.hasText(status)) {
            return status.trim();
        }
        Boolean accepted = object.getBoolean("accepted");
        if (Boolean.TRUE.equals(accepted)) {
            return "accepted";
        }
        if (StringUtils.hasText(object.getString("note_url")) || StringUtils.hasText(object.getString("note_id"))) {
            return "published";
        }
        return "failed";
    }

    private String resolveErrorType(String rawType, String errorCode, String errorMessage) {
        if (StringUtils.hasText(rawType)) {
            String normalized = rawType.trim().toLowerCase(Locale.ROOT);
            if ("transient".equals(normalized) || "business".equals(normalized) || "validation".equals(normalized)) {
                return normalized;
            }
        }

        String code = defaultText(errorCode, "").toUpperCase(Locale.ROOT);
        String message = defaultText(errorMessage, "").toUpperCase(Locale.ROOT);
        if (code.contains("INVALID") || code.contains("VALIDATION") || code.contains("TITLE") || code.contains("IMAGE")
                || message.contains("INVALID") || message.contains("TITLE") || message.contains("IMAGE")) {
            return "validation";
        }
        if (code.contains("TIMEOUT") || code.contains("NETWORK") || code.contains("UNAVAILABLE") || code.contains("405")
                || message.contains("TIMEOUT") || message.contains("NETWORK") || message.contains("UNAVAILABLE")
                || message.contains("405")) {
            return "transient";
        }
        return "business";
    }

    private void copyIfPresent(JSONObject source, JSONObject target, String key) {
        if (!source.containsKey(key)) {
            return;
        }
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private String sanitizeMessage(String message) {
        String compact = defaultText(message, "Agent 发布兜底失败").replaceAll("[\\r\\n\\t]+", " ").trim();
        if (compact.length() <= 240) {
            return compact;
        }
        return compact.substring(0, 240) + "...";
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

}
