package com.dasi.domain.xhspublish.service.execution.fallback;

import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;

import java.util.List;

public interface IXhsPublishAgentFallbackService {

    XhsPublishRemoteResult execute(String clientId,
                                   String publishRequestJson,
                                   String taskId,
                                   String attemptId,
                                   JSONObject sourceContext,
                                   List<XhsPublishContentAssetEntity> assetList);

}
