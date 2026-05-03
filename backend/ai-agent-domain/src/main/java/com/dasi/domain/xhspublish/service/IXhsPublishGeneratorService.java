package com.dasi.domain.xhspublish.service;

import com.dasi.domain.xhspublish.model.entity.XhsPublishIntelligentSubmitCommandEntity;
import com.dasi.domain.xhspublish.service.generator.GeneratedPublishContext;

public interface IXhsPublishGeneratorService {

    GeneratedPublishContext generate(XhsPublishIntelligentSubmitCommandEntity request, int imageCount);

}
