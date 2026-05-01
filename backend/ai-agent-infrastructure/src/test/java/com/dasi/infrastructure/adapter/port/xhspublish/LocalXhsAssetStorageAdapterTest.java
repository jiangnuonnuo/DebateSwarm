package com.dasi.infrastructure.adapter.port.xhspublish;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishConfigRepository;
import com.dasi.types.exception.WorkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalXhsAssetStorageAdapterTest {

    @TempDir
    Path tempDir;

    private LocalXhsAssetStorageAdapter storageAdapter;
    private Path assetBaseDir;

    @BeforeEach
    void setUp() {
        storageAdapter = new LocalXhsAssetStorageAdapter();
        assetBaseDir = tempDir.resolve("assets");
        IXhsPublishConfigRepository configRepository = new IXhsPublishConfigRepository() {
            @Override public String getAssetBaseDir() { return assetBaseDir.toString(); }
            @Override public String getAssetAccessBaseUrl() { return null; }
            @Override public String getSnapshotBaseDir() { return null; }
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
        ReflectionTestUtils.setField(storageAdapter, "xhsPublishConfigRepository", configRepository);
    }

    @Test
    void shouldSaveAndLoadResourceWithinBaseDir() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                "png-content".getBytes()
        );
        String storageRef = storageAdapter.save(multipartFile, "xhs/task-1");

        assertTrue(Path.of(storageRef).toAbsolutePath().normalize().startsWith(assetBaseDir.toAbsolutePath().normalize()));
        assertTrue(storageAdapter.loadAsResource(storageRef).exists());
    }

    @Test
    void shouldRejectOutOfBasePathOnLoad() throws Exception {
        Path outsideFile = tempDir.resolve("outside.png");
        Files.writeString(outsideFile, "outside");

        WorkException exception = assertThrows(WorkException.class, () -> storageAdapter.loadAsResource(outsideFile.toString()));
        assertEquals("素材路径非法", exception.getMessage());
    }

    @Test
    void shouldNotDeleteOutOfBasePath() throws Exception {
        Path outsideFile = tempDir.resolve("outside-delete.png");
        Files.writeString(outsideFile, "outside");

        storageAdapter.delete(outsideFile.toString());

        assertTrue(Files.exists(outsideFile));
    }

}
