package com.dasi.infrastructure.adapter.port.xhspublish;

import com.dasi.domain.xhspublish.config.XhsPublishProperties;
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
        XhsPublishProperties properties = new XhsPublishProperties();
        assetBaseDir = tempDir.resolve("assets");
        properties.setAssetBaseDir(assetBaseDir.toString());
        ReflectionTestUtils.setField(storageAdapter, "xhsPublishProperties", properties);
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
