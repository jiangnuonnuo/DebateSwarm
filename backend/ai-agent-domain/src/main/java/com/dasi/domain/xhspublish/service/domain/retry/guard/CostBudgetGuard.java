package com.dasi.domain.xhspublish.service.domain.retry.guard;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Order(50)
@Component
public class CostBudgetGuard implements IRetryDecisionGuard {

    @Override
    public RetryDecisionResult decide(RetryDecisionContext context) {
        if (context == null || context.getMaxCostBudget() == null) {
            return null;
        }
        BigDecimal usedCost = context.getTotalCostAmount() == null ? BigDecimal.ZERO : context.getTotalCostAmount();
        if (usedCost.compareTo(context.getMaxCostBudget()) >= 0) {
            return RetryDecisionResult.reject(RetryDecisionReasonCode.REJECT_COST_BUDGET_LIMIT,
                    "重试成本超限，usedCost=" + usedCost + ", maxCostBudget=" + context.getMaxCostBudget());
        }
        return null;
    }

}
