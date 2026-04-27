package com.dasi.domain.xhspublish.adapter.port;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface IXhsAssetStoragePort {

    String save(MultipartFile file, String businessPath);

    void delete(String storageRef);

    Resource loadAsResource(String storageRef);

}

