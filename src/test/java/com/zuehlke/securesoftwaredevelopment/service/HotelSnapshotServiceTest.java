package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.domain.HotelSnapshot;
import com.zuehlke.securesoftwaredevelopment.repository.HotelSnapshotRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.FileSystemUtils;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HotelSnapshotServiceTest {
    private static final int HOTEL_ID = 1;

    @Autowired
    private HotelSnapshotService snapshotService;

    @Autowired
    private HotelSnapshotRepository snapshotRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void cleanSnapshotState() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement clearBaseline = connection.prepareStatement(
                    "UPDATE hotel SET baselineSnapshotId = NULL WHERE id = ?")) {
                clearBaseline.setInt(1, HOTEL_ID);
                clearBaseline.executeUpdate();
            }
            try (PreparedStatement deleteSnapshots = connection.prepareStatement(
                    "DELETE FROM hotelSnapshot WHERE hotelId = ?")) {
                deleteSnapshots.setInt(1, HOTEL_ID);
                deleteSnapshots.executeUpdate();
            }
        }
        FileSystemUtils.deleteRecursively(snapshotService.snapshotDirectory(HOTEL_ID));
    }

    @Test
    void createsTarArchiveAndPersistsSnapshotMetadata() throws Exception {
        HotelSnapshot first = snapshotService.createSnapshot(HOTEL_ID);

        assertNotNull(first);
        assertNull(first.getParentSnapshotId());
        assertTrue(Files.isRegularFile(snapshotService.snapshotPath(first)));
        assertEquals(HotelSnapshotCsvExporter.SNAPSHOT_FILES, archiveEntries(snapshotService.snapshotPath(first)));

        snapshotRepository.setBaselineForHotel(HOTEL_ID, first.getId());
        HotelSnapshot second = snapshotService.createSnapshot(HOTEL_ID);

        assertEquals(first.getId(), second.getParentSnapshotId().longValue());
        assertEquals(first.getFileName(), second.getParentFileName());
        assertTrue(Files.isRegularFile(snapshotService.snapshotPath(second)));
        assertEquals(2, snapshotRepository.findAllForHotel(HOTEL_ID).size());
        assertEquals(first.getId(), snapshotRepository.findBaselineForHotel(HOTEL_ID).getId());
    }

    @Test
    void unknownHotelDoesNotCreateSnapshotMetadata() {
        assertThrows(IllegalArgumentException.class, () -> snapshotService.createSnapshot(99999));
        assertTrue(snapshotRepository.findAllForHotel(99999).isEmpty());
    }

    private List<String> archiveEntries(Path archive) throws Exception {
        Process process = new ProcessBuilder("tar", "-tzf", archive.toString())
                .redirectErrorStream(true)
                .start();

        List<String> entries;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            entries = reader.lines().collect(Collectors.toList());
        }

        assertEquals(0, process.waitFor());
        return entries;
    }
}
