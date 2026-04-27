package com.dasi.domain.xhspublish.adapter.port;

public interface IXhsSnapshotStoragePort {

    String save(String taskId, Integer roundNo, String stage, String content);

    void delete(String snapshotPath);

}

