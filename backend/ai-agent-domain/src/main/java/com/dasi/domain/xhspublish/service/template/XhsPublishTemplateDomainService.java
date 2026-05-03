package com.dasi.domain.xhspublish.service.template;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishTemplateRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplatePageQueryEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateSaveCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateEntity;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.types.exception.WorkException;
import com.dasi.types.result.PageResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class XhsPublishTemplateDomainService {

    @Resource
    private IXhsPublishTemplateRepository templateRepository;

    @Resource
    private XhsPublishTaskAccessSupport taskAccessSupport;

    @Resource
    private XhsPublishIdSupport idSupport;

    @Transactional(rollbackFor = Exception.class)
    public void saveTemplate(XhsPublishTemplateSaveCommandEntity dto) {
        Long userId = taskAccessSupport.requireUserId();
        if (StringUtils.hasText(dto.getTemplateId())) {
            XhsPublishTemplateEntity existing = templateRepository.queryByTemplateId(dto.getTemplateId());
            if (existing == null || !Objects.equals(userId, existing.getUserId())) {
                throw new WorkException("模板不存在");
            }
            existing.setTemplateName(dto.getTemplateName());
            existing.setPublishMode(dto.getPublishMode());
            existing.setTemplateConfigJson(dto.getTemplateConfigJson());
            existing.setTemplateStatus(dto.getTemplateStatus());
            existing.setIsDefault(dto.getIsDefault());
            templateRepository.update(existing);
            return;
        }

        XhsPublishTemplateEntity entity = XhsPublishTemplateEntity.builder()
                .templateId(idSupport.nextTemplateId())
                .userId(userId)
                .templateName(dto.getTemplateName())
                .publishMode(dto.getPublishMode())
                .templateConfigJson(dto.getTemplateConfigJson())
                .templateStatus(dto.getTemplateStatus())
                .isDefault(dto.getIsDefault())
                .build();
        templateRepository.insert(entity);
    }

    public PageResult<XhsPublishTemplateEntity> pageTemplate(XhsPublishTemplatePageQueryEntity dto) {
        Long userId = taskAccessSupport.requireUserId();
        int pageNum = Math.max(dto.getPageNum(), 1);
        int pageSize = Math.max(dto.getPageSize(), 1);
        int offset = (pageNum - 1) * pageSize;
        var list = templateRepository.page(userId, dto.getKeyword(), offset, pageSize);
        Integer total = templateRepository.count(userId, dto.getKeyword());
        int pageSum = (total + pageSize - 1) / pageSize;
        return PageResult.<XhsPublishTemplateEntity>builder()
                .list(list)
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pageSum(pageSum)
                .build();
    }

}
