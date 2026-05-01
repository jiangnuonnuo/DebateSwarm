package com.dasi.domain.xhspublish.service.execution.remote;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishConfigRepository;
import com.dasi.domain.xhspublish.adapter.port.IXhsMcpPort;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Slf4j
@Service
public class XhsPublishRemoteExecutor implements IXhsPublishRemoteExecutor {

    private static final String ERROR_TYPE_TRANSIENT = "transient";
    private static final String ERROR_TYPE_BUSINESS = "business";
    private static final String ERROR_TYPE_VALIDATION = "validation";

    @Resource
    private IXhsMcpPort xhsMcpPort;

    @Resource
    private IXhsPublishConfigRepository xhsPublishConfigRepository;

    @Override
    public XhsPublishRemoteResult executeImagePublish(String publishRequestJson) {
        try {
            String submitResultJson = xhsMcpPort.submitImagePublish(publishRequestJson);
            JSONObject finalResult = pollUntilSettled(submitResultJson);
            return mapRemoteResult(finalResult);
        } catch (Exception e) {
            log.warn("【小红书发布】远程发布调用异常，按 transient 处理：message={}", sanitizeMessage(e.getMessage()));
            JSONObject failure = new JSONObject();
            failure.put("status", "failed");
            failure.put("error_code", "REMOTE_CALL_EXCEPTION");
            failure.put("error_type", ERROR_TYPE_TRANSIENT);
            failure.put("error_message", sanitizeMessage(e.getMessage()));
            return XhsPublishRemoteResult.builder()
                    .status("failed")
                    .resultJson(JSON.toJSONString(failure))
                    .errorCode("REMOTE_CALL_EXCEPTION")
                    .errorType(ERROR_TYPE_TRANSIENT)
                    .errorMessage(sanitizeMessage(e.getMessage()))
                    .executor("backend_mcp")
                    .build();
        }
    }

    private JSONObject pollUntilSettled(String submitResultJson) {
        JSONObject submitResult = JSON.parseObject(submitResultJson);
        if (submitResult == null) {
            JSONObject fallback = new JSONObject();
            fallback.put("status", "failed");
            fallback.put("error_code", "REMOTE_RESPONSE_EMPTY");
            fallback.put("error_message", "远程返回为空");
            return fallback;
        }
        String jobId = submitResult.getString("job_id");
        if (jobId == null || jobId.isBlank()) {
            return submitResult;
        }

        JSONObject latest = submitResult;
        for (int i = 0; i < defaultInt(xhsPublishConfigRepository.getMcpPollRounds(), 2); i++) {
            String statusJson = xhsMcpPort.queryJobStatus(jobId);
            latest = JSON.parseObject(statusJson);
            String status = extractStatus(latest);
            if ("failed".equalsIgnoreCase(status) || "published".equalsIgnoreCase(status) || "verified".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status)) {
                JSONObject verifyPayload = new JSONObject();
                verifyPayload.put("job_id", jobId);
                String verifyResultJson = xhsMcpPort.verifyPublishedNote(JSON.toJSONString(verifyPayload));
                JSONObject verifyResult = JSON.parseObject(verifyResultJson);
                latest.put("verify_result", verifyResult);
                String verifyStatus = verifyResult.getString("verify_status");
                if ("verified".equalsIgnoreCase(verifyStatus)) {
                    latest.put("status", "published");
                }
                return latest;
            }
            sleep(defaultInt(xhsPublishConfigRepository.getMcpPollIntervalMillis(), 1500));
        }
        return latest;
    }

    private XhsPublishRemoteResult mapRemoteResult(JSONObject finalResult) {
        String finalStatus = extractStatus(finalResult);
        boolean failed = "failed".equalsIgnoreCase(finalStatus);
        String errorCode = failed ? resolveErrorCode(finalResult) : null;
        String errorType = failed ? resolveErrorType(errorCode, finalResult) : null;
        String errorMessage = failed ? extractErrorMessage(finalResult) : null;
        JSONObject resultView = buildResultView(finalResult, finalStatus, errorCode, errorType, errorMessage);

        return XhsPublishRemoteResult.builder()
                .status(finalStatus)
                .resultJson(JSON.toJSONString(resultView))
                .errorCode(errorCode)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .executor("backend_mcp")
                .build();
    }

    private String extractStatus(JSONObject result) {
        if (result == null) {
            return "failed";
        }
        String status = result.getString("status");
        if (status != null && !status.isBlank()) {
            return status;
        }
        JSONObject verifyResult = result.getJSONObject("verify_result");
        if (verifyResult != null) {
            String verifyStatus = verifyResult.getString("verify_status");
            if (verifyStatus != null && !verifyStatus.isBlank()) {
                return verifyStatus;
            }
        }
        Boolean accepted = result.getBoolean("accepted");
        if (Boolean.TRUE.equals(accepted)) {
            return "accepted";
        }
        Boolean ok = result.getBoolean("ok");
        return Boolean.FALSE.equals(ok) ? "failed" : "accepted";
    }

    private String resolveErrorCode(JSONObject result) {
        if (result == null) {
            return "REMOTE_UNKNOWN_ERROR";
        }
        String errorCode = result.getString("error_code");
        if (StringUtils.hasText(errorCode)) {
            return errorCode;
        }
        String nestedCode = null;
        JSONObject verifyResult = result.getJSONObject("verify_result");
        if (verifyResult != null) {
            nestedCode = verifyResult.getString("error_code");
        }
        if (StringUtils.hasText(nestedCode)) {
            return nestedCode;
        }
        return "REMOTE_UNKNOWN_ERROR";
    }

    private String resolveErrorType(String errorCode, JSONObject result) {
        String rawType = result == null ? null : result.getString("error_type");
        if (StringUtils.hasText(rawType)) {
            String normalized = rawType.trim().toLowerCase(Locale.ROOT);
            if (ERROR_TYPE_TRANSIENT.equals(normalized) || ERROR_TYPE_BUSINESS.equals(normalized) || ERROR_TYPE_VALIDATION.equals(normalized)) {
                return normalized;
            }
        }

        String normalizedCode = errorCode == null ? "" : errorCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.contains("INVALID")
                || normalizedCode.contains("VALIDATION")
                || normalizedCode.contains("TITLE")
                || normalizedCode.contains("SCHEDULE")
                || normalizedCode.contains("IMAGE")) {
            return ERROR_TYPE_VALIDATION;
        }
        if (normalizedCode.contains("TIMEOUT")
                || normalizedCode.contains("RATE_LIMIT")
                || normalizedCode.contains("NETWORK")
                || normalizedCode.contains("TEMP")
                || normalizedCode.contains("TRANSIENT")
                || normalizedCode.contains("UNAVAILABLE")) {
            return ERROR_TYPE_TRANSIENT;
        }
        return ERROR_TYPE_BUSINESS;
    }

    private String extractErrorMessage(JSONObject result) {
        if (result == null) {
            return "发布失败";
        }
        String errorMessage = result.getString("error_message");
        if (!StringUtils.hasText(errorMessage)) {
            JSONObject verifyResult = result.getJSONObject("verify_result");
            if (verifyResult != null) {
                errorMessage = verifyResult.getString("error_message");
            }
        }
        if (!StringUtils.hasText(errorMessage)) {
            errorMessage = "发布失败";
        }
        return sanitizeMessage(errorMessage);
    }

    private JSONObject buildResultView(JSONObject finalResult,
                                       String finalStatus,
                                       String errorCode,
                                       String errorType,
                                       String errorMessage) {
        JSONObject view = new JSONObject();
        view.put("status", finalStatus);
        view.put("executor", "backend_mcp");
        if (finalResult == null) {
            if (StringUtils.hasText(errorCode)) {
                view.put("error_code", errorCode);
            }
            if (StringUtils.hasText(errorType)) {
                view.put("error_type", errorType);
            }
            if (StringUtils.hasText(errorMessage)) {
                view.put("error_message", errorMessage);
            }
            return view;
        }

        copyIfPresent(finalResult, view, "job_id");
        copyIfPresent(finalResult, view, "accepted");
        copyIfPresent(finalResult, view, "note_id");
        copyIfPresent(finalResult, view, "note_url");
        copyIfPresent(finalResult, view, "publish_time");

        JSONObject verifyResult = finalResult.getJSONObject("verify_result");
        if (verifyResult != null) {
            JSONObject verifyView = new JSONObject();
            copyIfPresent(verifyResult, verifyView, "verify_status");
            copyIfPresent(verifyResult, verifyView, "note_id");
            copyIfPresent(verifyResult, verifyView, "note_url");
            if (!verifyView.isEmpty()) {
                view.put("verify_result", verifyView);
            }
        }

        if (StringUtils.hasText(errorCode)) {
            view.put("error_code", errorCode);
        }
        if (StringUtils.hasText(errorType)) {
            view.put("error_type", errorType);
        }
        if (StringUtils.hasText(errorMessage)) {
            view.put("error_message", errorMessage);
        }
        return view;
    }

    private void copyIfPresent(JSONObject source, JSONObject target, String key) {
        if (source == null || target == null || !source.containsKey(key)) {
            return;
        }
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private String sanitizeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "发布失败";
        }
        String compact = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (compact.length() <= 240) {
            return compact;
        }
        return compact.substring(0, 240) + "...";
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}

