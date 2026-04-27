package com.dasi.domain.xhspublish.adapter.port;

public interface IXhsMcpPort {

    String submitImagePublish(String publishRequestJson);

    String queryJobStatus(String jobId);

    String verifyPublishedNote(String verifyRequestJson);

}

