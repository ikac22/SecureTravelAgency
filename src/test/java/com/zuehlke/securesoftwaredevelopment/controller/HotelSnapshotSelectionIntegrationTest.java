package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.HotelSnapshot;
import com.zuehlke.securesoftwaredevelopment.service.HotelSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HotelSnapshotSelectionIntegrationTest {
    private static final int HOTEL_ID = 1;
    private static final Path SNAPSHOT_DIRECTORY = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "secure-travel-agency-snapshots",
            String.valueOf(HOTEL_ID)
    );

    @Autowired
    private HotelSnapshotController snapshotController;

    @Autowired
    private HotelSnapshotService snapshotService;

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
        FileSystemUtils.deleteRecursively(SNAPSHOT_DIRECTORY);
    }

    @Test
    void selectedCsvValueCanChangeTarProcessing() throws Exception {
        HotelSnapshot snapshot = snapshotService.createSnapshot(HOTEL_ID);
        List<String> selectedFiles = Arrays.asList(
                "ratings.csv",
                "--use-compress-program=tr a b.csv"
        );

        ResponseEntity<byte[]> response = snapshotController.downloadSelectedSnapshot(
                HOTEL_ID,
                snapshot.getId(),
                selectedFiles
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        String filteredTar = new String(gunzip(response.getBody()), StandardCharsets.ISO_8859_1);
        assertTrue(filteredTar.contains("rbtings.csv"));
        assertFalse(filteredTar.contains("ratings.csv"));
    }

    private byte[] gunzip(byte[] content) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(content));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
