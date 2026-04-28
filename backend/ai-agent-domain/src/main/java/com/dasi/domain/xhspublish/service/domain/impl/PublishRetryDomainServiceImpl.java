package com.dasi.domain.xhspublish.service.domain.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.service.domain.IPublishRetryDomainService;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import com.dasi.domain.xhspublish.service.domain.retry.guard.IRetryDecisionGuard;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PublishRetryDomainServiceImpl implements IPublishRetryDomainService {

    @Resource
    private List<IRetryDecisionGuard> retryDecisionGuards;

    @Override
    public RetryDecisionResult evaluate(RetryDecisionContext context) {
        RetryDecisionContext decisionContext = enrichContext(context);
        for (IRetryDecisionGuard guard : retryDecisionGuards) {
            RetryDecisionResult guardResult = guard.decide(decisionContext);
            if (guardResult != null && !guardResult.isAllowed()) {
                return guardResult;
            }
        }
        if (!StringUtils.hasText(decisionContext.getRetryPolicyJson())) {
            return RetryDecisionResult.allow(RetryDecisionReasonCode.ALLOW_POLICY_NOT_SET, "未配置 retryPolicy，默认允许重试");
        }
        return RetryDecisionResult.allow(RetryDecisionReasonCode.ALLOW_WITHIN_POLICY, "重试条件校验通过");
    }

    private RetryDecisionContext enrichContext(RetryDecisionContext context) {
        RetryDecisionContext target = context == null ? new RetryDecisionContext() : context;
        target.setTotalDurationMs(target.getTotalDurationMs() == null ? 0L : target.getTotalDurationMs());
        target.setTotalCostAmount(target.getTotalCostAmount() == null ? BigDecimal.ZERO : target.getTotalCostAmount());
        target.setActiveAttempt(Boolean.TRUE.equals(target.getActiveAttempt()));

        if (!StringUtils.hasText(target.getRetryPolicyJson())) {
            target.setMaxRetry(null);
            target.setMaxTotalDurationMs(null);
            target.setMaxCostBudget(null);
            return target;
        }

        JSONObject policy = JSON.parseObject(target.getRetryPolicyJson());
        if (policy == null) {
            target.setMaxRetry(null);
            target.setMaxTotalDurationMs(null);
            target.setMaxCostBudget(null);
            return target;
        }
        target.setMaxRetry(firstNonNull(policy.getInteger("maxRetry"), policy.getInteger("max_retry"), null));
        Integer maxDuration = firstNonNull(policy.getInteger("maxTotalDurationMs"), policy.getInteger("max_total_duration_ms"), null);
        target.setMaxTotalDurationMs(maxDuration == null ? null : maxDuration.longValue());
        target.setMaxCostBudget(firstNonNullDecimal(parseDecimal(policy, "maxCostBudget"), parseDecimal(policy, "max_cost_budget"), null));
        return target;
    }

    private Integer firstNonNull(Integer first, Integer second, Integer defaultValue) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return defaultValue;
    }

    private BigDecimal firstNonNullDecimal(BigDecimal first, BigDecimal second, BigDecimal defaultValue) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return defaultValue;
    }

    private BigDecimal parseDecimal(JSONObject source, String key) {
        if (source == null || key == null || !source.containsKey(key)) {
            return null;
        }
        Object raw = source.get(key);
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (Exception ignore) {
            return null;
        }
    }

}
