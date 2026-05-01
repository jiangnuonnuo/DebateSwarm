package com.dasi.domain.xhspublish.service.generator;

import com.dasi.domain.xhspublish.model.dto.IntelligentXhsPublishSubmitDTO;

public interface IXhsPublishGeneratorService {

    GeneratedPublishContext generate(IntelligentXhsPublishSubmitDTO request, int imageCount);

}
