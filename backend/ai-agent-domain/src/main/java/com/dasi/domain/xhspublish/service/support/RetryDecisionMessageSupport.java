package com.dasi.domain.xhspublish.service.support;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import org.springframework.util.StringUtils;

import java.util.Map;

public final class RetryDecisionMessageSupport {

    private static final Map<String, String> REJECT_REASON_MESSAGE_MAP = Map.of(
            RetryDecisionReasonCode.REJECT_ACTIVE_ATTEMPT, "任务存在活跃执行，请稍后重试",
            RetryDecisionReasonCode.REJECT_NON_RETRYABLE_ERROR, "当前失败类型不支持重试",
            RetryDecisionReasonCode.REJECT_RETRY_COUNT_LIMIT, "已达到最大重试次数",
            RetryDecisionReasonCode.REJECT_DURATION_BUDGET_LIMIT, "已达到重试总耗时上限",
            RetryDecisionReasonCode.REJECT_COST_BUDGET_LIMIT, "已达到重试成本预算上限"
    );

    private RetryDecisionMessageSupport() {
    }

    public static String toUserMessage(RetryDecisionResult decisionResult) {
        if (decisionResult == null) {
            return "当前任务不满足重试条件";
        }
        String reasonCode = decisionResult.getReasonCode();
        String defaultMessage = StringUtils.hasText(decisionResult.getReasonMessage())
                ? decisionResult.getReasonMessage()
                : "当前任务不满足重试条件";
        String resolvedMessage = StringUtils.hasText(reasonCode)
                ? REJECT_REASON_MESSAGE_MAP.getOrDefault(reasonCode, defaultMessage)
                : defaultMessage;
        if (!StringUtils.hasText(reasonCode)) {
            return resolvedMessage;
        }
        return resolvedMessage + "（" + reasonCode + "）";
    }

}
