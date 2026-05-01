package com.dasi.infrastructure.adapter.port.xhspublish;

import com.dasi.domain.xhspublish.config.XhsPublishProperties;
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
        XhsPublishProperties properties = new XhsPublishProperties();
        snapshotBaseDir = tempDir.resolve("snapshots");
        properties.setSnapshotBaseDir(snapshotBaseDir.toString());
        ReflectionTestUtils.setField(snapshotStorageAdapter, "xhsPublishProperties", properties);
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
