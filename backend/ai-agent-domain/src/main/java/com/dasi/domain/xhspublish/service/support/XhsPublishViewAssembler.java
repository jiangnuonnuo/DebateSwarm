package com.dasi.domain.xhspublish.service.support;

import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateEntity;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAssetVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAttemptVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishReviewVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskPageVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTemplateVO;
import org.springframework.stereotype.Component;

@Component
public class XhsPublishViewAssembler {

    public XhsPublishTaskPageVO toTaskPageVO(XhsPublishTaskEntity entity) {
        return XhsPublishTaskPageVO.builder()
                .taskId(entity.getTaskId())
                .taskName(entity.getTaskName())
                .publishMode(entity.getPublishMode())
                .publishType(entity.getPublishType())
                .taskStatus(entity.getTaskStatus())
                .currentStage(entity.getCurrentStage())
                .scheduledPublishAt(entity.getScheduledPublishAt())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    public XhsPublishAttemptVO toAttemptVO(XhsPublishAttemptEntity entity) {
        return XhsPublishAttemptVO.builder()
                .attemptId(entity.getAttemptId())
                .attemptNo(entity.getAttemptNo())
                .attemptStatus(entity.getAttemptStatus())
                .stage(entity.getStage())
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .costAmount(entity.getCostAmount())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .build();
    }

    public XhsPublishReviewVO toReviewVO(XhsPublishReviewEntity entity) {
        return XhsPublishReviewVO.builder()
                .reviewId(entity.getReviewId())
                .reviewStage(entity.getReviewStage())
                .reviewMode(entity.getReviewMode())
                .reviewStatus(entity.getReviewStatus())
                .reviewComment(entity.getReviewComment())
                .decidedTime(entity.getDecidedTime())
                .build();
    }

    public XhsPublishAssetVO toAssetVO(XhsPublishContentAssetEntity entity) {
        return XhsPublishAssetVO.builder()
                .assetId(entity.getAssetId())
                .assetType(entity.getAssetType())
                .sourceType(entity.getSourceType())
                .storageType(entity.getStorageType())
                .accessUrl(entity.getAccessUrl())
                .sortNo(entity.getSortNo())
                .assetStatus(entity.getAssetStatus())
                .expireTime(entity.getExpireTime())
                .build();
    }

    public XhsPublishTemplateVO toTemplateVO(XhsPublishTemplateEntity entity) {
        return XhsPublishTemplateVO.builder()
                .templateId(entity.getTemplateId())
                .templateName(entity.getTemplateName())
                .publishMode(entity.getPublishMode())
                .templateStatus(entity.getTemplateStatus())
                .isDefault(entity.getIsDefault())
                .updateTime(entity.getUpdateTime())
                .build();
    }

}
