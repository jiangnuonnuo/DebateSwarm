package com.dasi.domain.xhspublish.service.domain;

public interface IPublishValidationDomainService {

    void validateTaskRequest(String publishType, String requestJson);

    void validatePublishPayload(String publishType, String publishRequestJson);

}
