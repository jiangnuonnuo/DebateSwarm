package com.dasi.domain.xhspublish.model.aggregate;

import com.dasi.domain.xhspublish.model.entity.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishTaskAggregate {

    private XhsPublishTaskEntity task;

    private XhsPublishAttemptEntity latestAttempt;

    private List<XhsPublishAttemptEntity> attemptList;

    private List<XhsPublishReviewEntity> reviewList;

    private List<XhsPublishContentAssetEntity> assetList;

    private XhsPublishAccountBindingEntity binding;

}
