package com.dasi.domain.xhspublish.service.support;

import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishAccountBindingRepository;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishConfigRepository;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class XhsPublishBindingSupport {

    @Resource
    private IXhsPublishAccountBindingRepository accountBindingRepository;

    @Resource
    private IXhsPublishConfigRepository xhsPublishConfigRepository;

    @Resource
    private XhsPublishIdSupport idSupport;

    public XhsPublishAccountBindingEntity resolveBinding(Long userId, String bindingId) {
        if (userId == null) {
            throw new WorkException("当前用户不存在，无法选择发布账号");
        }

        XhsPublishAccountBindingEntity bindingEntity;
        if (bindingId != null && !bindingId.isBlank()) {
            bindingEntity = accountBindingRepository.queryByBindingId(bindingId);
            if (bindingEntity == null || !userId.equals(bindingEntity.getUserId())) {
                throw new WorkException("发布账号绑定不存在");
            }
        } else {
            bindingEntity = accountBindingRepository.queryDefaultByUserId(userId);
            if (bindingEntity == null) {
                bindingEntity = initDefaultBinding(userId);
            }
        }

        if (bindingEntity.getBindStatus() != null && bindingEntity.getBindStatus() == 0) {
            throw new WorkException("发布账号绑定已禁用");
        }
        return bindingEntity;
    }

    public XhsPublishAccountBindingEntity initDefaultBinding(Long userId) {
        XhsPublishAccountBindingEntity entity = XhsPublishAccountBindingEntity.builder()
                .bindingId(idSupport.nextBindingId())
                .userId(userId)
                .accountName(xhsPublishConfigRepository.getDefaultAccountName())
                .mcpTenantId(xhsPublishConfigRepository.getDefaultTenantId())
                .mcpAccountId(xhsPublishConfigRepository.getDefaultAccountId())
                .bindStatus(1)
                .isDefault(1)
                .lastCheckTime(LocalDateTime.now())
                .build();
        accountBindingRepository.insert(entity);
        return accountBindingRepository.queryByBindingId(entity.getBindingId());
    }

}

