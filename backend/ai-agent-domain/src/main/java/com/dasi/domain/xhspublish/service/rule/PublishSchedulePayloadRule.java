package com.dasi.domain.xhspublish.service.rule;

import org.springframework.stereotype.Component;

@Component
public class PublishSchedulePayloadRule implements IXhsPublishPayloadRule {

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public void apply(XhsPublishRuleContext context) {
        String scheduleAt = XhsPublishRuleSupport.readString(context.getSourceContext(), "schedule_at", "scheduledPublishAt", "scheduled_publish_at");
        String normalized = XhsPublishRuleSupport.normalizeSchedule(scheduleAt);
        if (normalized != null) {
            context.getPublishPayload().put("schedule_at", normalized);
        }
    }

}
