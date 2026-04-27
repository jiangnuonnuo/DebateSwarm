package com.dasi.infrastructure.adapter.repository.support;

import org.springframework.beans.BeanUtils;

public final class XhsPublishRepositoryConverter {

    private XhsPublishRepositoryConverter() {
    }

    public static <T> T copy(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("小红书发布对象转换失败", e);
        }
    }

}

