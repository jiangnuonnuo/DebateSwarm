package com.dasi.domain.xhspublish.service.generator;

import com.dasi.domain.xhspublish.model.entity.XhsPublishIntelligentSubmitCommandEntity;

public interface IXhsPublishGeneratorService {

    GeneratedPublishContext generate(XhsPublishIntelligentSubmitCommandEntity request, int imageCount);

}
