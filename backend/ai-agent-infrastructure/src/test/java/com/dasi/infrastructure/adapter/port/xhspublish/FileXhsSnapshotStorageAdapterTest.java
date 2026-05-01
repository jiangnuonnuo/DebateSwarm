package com.dasi.infrastructure.adapter.port.xhspublish;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileXhsSnapshotStorageAdapterTest {

    @TempDir
    Path tempDir;

    private FileXhsSnapshotStorageAdapter snapshotStorageAdapter;
    private Path snapshotBaseDir;

    @BeforeEach
    void setUp() {
        snapshotStorageAdapter = new FileXhsSnapshotStorageAdapter();
        snapshotBaseDir = tempDir.resolve("snapshots");
        IXhsPublishConfigRepository configRepository = new IXhsPublishConfigRepository() {
            @Override public String getAssetBaseDir() { return null; }
            @Override public String getAssetAccessBaseUrl() { return null; }
            @Override public String getSnapshotBaseDir() { return snapshotBaseDir.toString(); }
            @Override public Integer getAssetRetentionHours() { return null; }
            @Override public Integer getSnapshotRetentionHours() { return null; }
            @Override public String getDefaultTenantId() { return null; }
            @Override public String getDefaultAccountId() { return null; }
            @Override public String getDefaultAccountName() { return null; }
            @Override public String getVectorSchemaName() { return null; }
            @Override public String getVectorTableName() { return null; }
            @Override public Integer getMcpPollRounds() { return null; }
            @Override public Integer getMcpPollIntervalMillis() { return null; }
        };
        ReflectionTestUtils.setField(snapshotStorageAdapter, "xhsPublishConfigRepository", configRepository);
    }

    @Test
    void shouldSaveAndDeleteSnapshotWithinBaseDir() {
        String snapshotPath = snapshotStorageAdapter.save("task-1", 2, "payload_built", "{\"status\":\"ok\"}");
        Path savedFile = Path.of(snapshotPath).toAbsolutePath().normalize();
        assertTrue(savedFile.startsWith(snapshotBaseDir.toAbsolutePath().normalize()));
        assertTrue(Files.exists(savedFile));

        snapshotStorageAdapter.delete(snapshotPath);
        assertFalse(Files.exists(savedFile));
    }

    @Test
    void shouldNotDeleteOutOfBaseSnapshotPath() throws Exception {
        Path outsideFile = tempDir.resolve("outside-snapshot.json");
        Files.writeString(outsideFile, "{}");

        snapshotStorageAdapter.delete(outsideFile.toString());
        assertTrue(Files.exists(outsideFile));
    }

}
