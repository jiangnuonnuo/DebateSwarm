package com.dasi.domain.xhspublish.service.context;

import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishSnapshotEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishExecutionContext {

    private XhsPublishTaskEntity task;

    private XhsPublishAttemptEntity attempt;

    private XhsPublishAccountBindingEntity binding;

    private List<XhsPublishContentAssetEntity> assetList;

    private JSONObject sourceContext;

    private LocalDateTime startTime;

    private String publishRequestJson;

    private XhsPublishRemoteResult remoteResult;

    private Long remoteDurationMs;

    private XhsPublishSnapshotEntity payloadSnapshot;

}
