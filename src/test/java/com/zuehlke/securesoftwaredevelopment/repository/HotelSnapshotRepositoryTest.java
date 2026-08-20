package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.HotelSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HotelSnapshotRepositoryTest {
    private static final int HOTEL_ID = 1;

    @Autowired
    private HotelSnapshotRepository snapshotRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    void storesSnapshotLineageAndTracksBaselineOnHotel() throws Exception {
        clearSnapshotMetadata();
        try {
            LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 8, 21, 10, 0);
            long firstId = snapshotRepository.create(
                    HOTEL_ID,
                    "hotel-1-snapshot-1.tar.gz",
                    firstCreatedAt,
                    null
            );
            snapshotRepository.setBaselineForHotel(HOTEL_ID, firstId);

            long secondId = snapshotRepository.create(
                    HOTEL_ID,
                    "hotel-1-snapshot-2.tar.gz",
                    firstCreatedAt.plusMinutes(1),
                    firstId
            );

            HotelSnapshot first = snapshotRepository.findByIdAndHotel(firstId, HOTEL_ID);
            HotelSnapshot second = snapshotRepository.findByIdAndHotel(secondId, HOTEL_ID);
            HotelSnapshot baseline = snapshotRepository.findBaselineForHotel(HOTEL_ID);
            List<HotelSnapshot> snapshots = snapshotRepository.findAllForHotel(HOTEL_ID);

            assertNotNull(first);
            assertNotNull(second);
            assertNotNull(baseline);
            assertNull(first.getParentSnapshotId());
            assertTrue(first.isBaseline());
            assertEquals(firstId, baseline.getId());
            assertEquals(firstId, second.getParentSnapshotId().longValue());
            assertEquals(first.getFileName(), second.getParentFileName());
            assertFalse(second.isBaseline());
            assertEquals(2, snapshots.size());
            assertEquals(secondId, snapshots.get(0).getId());

            snapshotRepository.setBaselineForHotel(HOTEL_ID, secondId);

            assertEquals(secondId, snapshotRepository.findBaselineForHotel(HOTEL_ID).getId());
            assertFalse(snapshotRepository.findByIdAndHotel(firstId, HOTEL_ID).isBaseline());
            assertTrue(snapshotRepository.findByIdAndHotel(secondId, HOTEL_ID).isBaseline());
        } finally {
            clearSnapshotMetadata();
        }
    }

    private void clearSnapshotMetadata() throws Exception {
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
    }
}
