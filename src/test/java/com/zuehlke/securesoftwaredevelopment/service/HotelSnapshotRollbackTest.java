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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HotelSnapshotRollbackTest {
    private static final int HOTEL_ID = 1;

    @Autowired
    private HotelSnapshotService snapshotService;

    @Autowired
    private HotelSnapshotCsvExporter csvExporter;

    @Autowired
    private HotelSnapshotCsvImporter csvImporter;

    @Autowired
    private HotelSnapshotRepository snapshotRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void resetState() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            execute(connection, "UPDATE hotel SET baselineSnapshotId = NULL WHERE id = ?", HOTEL_ID);
            execute(connection, "DELETE FROM hotelSnapshot WHERE hotelId = ?", HOTEL_ID);
            execute(connection, "DELETE FROM reservation WHERE hotelId = ?", HOTEL_ID);
            execute(connection, "DELETE FROM ratings WHERE hotelId = ?", HOTEL_ID);
            execute(connection, "DELETE FROM roomType WHERE hotelId = ?", HOTEL_ID);

            try (PreparedStatement hotel = connection.prepareStatement(
                    "UPDATE hotel SET cityId = 1, name = ?, description = ?, address = ? WHERE id = ?")) {
                hotel.setString(1, "Danube View Hotel");
                hotel.setString(2, "Modern hotel near the river promenade.");
                hotel.setString(3, "Cara Urosa 10, Belgrade");
                hotel.setInt(4, HOTEL_ID);
                hotel.executeUpdate();
            }

            try (PreparedStatement room = connection.prepareStatement(
                    "INSERT INTO roomType(id, hotelId, name, capacity, pricePerNight, totalRooms) VALUES(?, ?, ?, ?, ?, ?)")) {
                room.setInt(1, 1);
                room.setInt(2, HOTEL_ID);
                room.setString(3, "Standard Double");
                room.setInt(4, 2);
                room.setBigDecimal(5, new BigDecimal("79.99"));
                room.setInt(6, 20);
                room.executeUpdate();

                room.setInt(1, 2);
                room.setString(3, "Family Suite");
                room.setInt(4, 4);
                room.setBigDecimal(5, new BigDecimal("129.50"));
                room.setInt(6, 5);
                room.executeUpdate();
            }

            try (PreparedStatement reservation = connection.prepareStatement(
                    "INSERT INTO reservation(id, userId, hotelId, roomTypeId, startDate, endDate, roomsCount, guestsCount, totalPrice) " +
                            "VALUES(1, 1, 1, 1, DATE '2026-03-10', DATE '2026-03-13', 1, 2, 239.97)")) {
                reservation.executeUpdate();
            }

            try (PreparedStatement rating = connection.prepareStatement(
                    "INSERT INTO ratings(hotelId, userId, rating) VALUES(?, ?, ?)")) {
                for (int[] value : Arrays.asList(new int[]{1, 5}, new int[]{2, 4}, new int[]{3, 5})) {
                    rating.setInt(1, HOTEL_ID);
                    rating.setInt(2, value[0]);
                    rating.setInt(3, value[1]);
                    rating.executeUpdate();
                }
            }

            connection.commit();
        }

        FileSystemUtils.deleteRecursively(snapshotService.snapshotDirectory(HOTEL_ID));
    }

    @Test
    void restoresSnapshotStateAndMakesItTheBaseline() throws Exception {
        HotelSnapshot snapshot = snapshotService.createSnapshot(HOTEL_ID);

        mutateCurrentState();
        assertEquals("Changed Hotel", scalarString("SELECT name FROM hotel WHERE id = 1"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM roomType WHERE hotelId = 1"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM reservation WHERE hotelId = 1"));

        HotelSnapshot restored = snapshotService.rollbackToSnapshot(HOTEL_ID, snapshot.getId());

        assertNotNull(restored);
        assertTrue(restored.isBaseline());
        assertEquals("Danube View Hotel", scalarString("SELECT name FROM hotel WHERE id = 1"));
        assertEquals("Cara Urosa 10, Belgrade", scalarString("SELECT address FROM hotel WHERE id = 1"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM roomType WHERE hotelId = 1"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM reservation WHERE hotelId = 1"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM ratings WHERE hotelId = 1"));
        assertEquals(new BigDecimal("79.99"), scalarDecimal(
                "SELECT pricePerNight FROM roomType WHERE id = 1 AND hotelId = 1"));
        assertEquals(snapshot.getId(), snapshotRepository.findBaselineForHotel(HOTEL_ID).getId());
        assertNull(snapshotService.rollbackToSnapshot(2, snapshot.getId()));
    }

    @Test
    void failedImportRollsBackAllDatabaseChanges() throws Exception {
        HotelSnapshot snapshot = snapshotService.createSnapshot(HOTEL_ID);
        Path workspace = Files.createTempDirectory("hotel-snapshot-import-test-");
        try {
            csvExporter.exportHotelState(HOTEL_ID, workspace);
            Path reservations = workspace.resolve(HotelSnapshotCsvExporter.RESERVATIONS_CSV);
            List<String> lines = Files.readAllLines(reservations, StandardCharsets.UTF_8);
            lines.set(1, "1,1,2,1,2026-03-10,2026-03-13,1,2,239.97");
            Files.write(reservations, lines, StandardCharsets.UTF_8);

            mutateCurrentState();

            assertThrows(IllegalArgumentException.class,
                    () -> csvImporter.restoreHotelState(HOTEL_ID, snapshot.getId(), workspace));

            assertEquals("Changed Hotel", scalarString("SELECT name FROM hotel WHERE id = 1"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM roomType WHERE hotelId = 1"));
            assertEquals(new BigDecimal("999.00"), scalarDecimal(
                    "SELECT pricePerNight FROM roomType WHERE hotelId = 1"));
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM reservation WHERE hotelId = 1"));
            assertNull(snapshotRepository.findBaselineForHotel(HOTEL_ID));
        } finally {
            FileSystemUtils.deleteRecursively(workspace);
        }
    }

    private void mutateCurrentState() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            execute(connection, "DELETE FROM reservation WHERE hotelId = ?", HOTEL_ID);
            execute(connection, "DELETE FROM ratings WHERE hotelId = ?", HOTEL_ID);
            execute(connection, "DELETE FROM roomType WHERE hotelId = ?", HOTEL_ID);

            try (PreparedStatement hotel = connection.prepareStatement(
                    "UPDATE hotel SET name = 'Changed Hotel', description = 'Changed description', address = 'Changed address' " +
                            "WHERE id = ?")) {
                hotel.setInt(1, HOTEL_ID);
                hotel.executeUpdate();
            }

            try (PreparedStatement room = connection.prepareStatement(
                    "INSERT INTO roomType(id, hotelId, name, capacity, pricePerNight, totalRooms) VALUES(101, 1, 'Temporary', 1, 999.00, 1)")) {
                room.executeUpdate();
            }
            connection.commit();
        }
    }

    private void execute(Connection connection, String sql, int hotelId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, hotelId);
            statement.executeUpdate();
        }
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String scalarString(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private BigDecimal scalarDecimal(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getBigDecimal(1);
        }
    }
}
