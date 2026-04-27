package com.dasi.domain.xhspublish.service.execution.remote;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.config.XhsPublishProperties;
import com.dasi.domain.xhspublish.adapter.port.IXhsMcpPort;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class XhsPublishRemoteExecutor implements IXhsPublishRemoteExecutor {

    @Resource
    private IXhsMcpPort xhsMcpPort;

    @Resource
    private XhsPublishProperties xhsPublishProperties;

    @Override
    public XhsPublishRemoteResult executeImagePublish(String publishRequestJson) {
        String submitResultJson = xhsMcpPort.submitImagePublish(publishRequestJson);
        JSONObject finalResult = pollUntilSettled(submitResultJson);
        String finalStatus = extractStatus(finalResult);
        boolean failed = "failed".equalsIgnoreCase(finalStatus);
        return XhsPublishRemoteResult.builder()
                .status(finalStatus)
                .resultJson(JSON.toJSONString(finalResult))
                .errorCode(failed && finalResult != null ? finalResult.getString("error_code") : null)
                .errorMessage(failed ? extractErrorMessage(finalResult) : null)
                .build();
    }

    private JSONObject pollUntilSettled(String submitResultJson) {
        JSONObject submitResult = JSON.parseObject(submitResultJson);
        String jobId = submitResult.getString("job_id");
        if (jobId == null || jobId.isBlank()) {
            return submitResult;
        }

        JSONObject latest = submitResult;
        for (int i = 0; i < defaultInt(xhsPublishProperties.getMcpPollRounds(), 2); i++) {
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
            sleep(defaultInt(xhsPublishProperties.getMcpPollIntervalMillis(), 1500));
        }
        return latest;
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

    private String extractErrorMessage(JSONObject result) {
        if (result == null) {
            return "发布失败";
        }
        String errorMessage = result.getString("error_message");
        return (errorMessage == null || errorMessage.isBlank()) ? JSON.toJSONString(result) : errorMessage;
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

