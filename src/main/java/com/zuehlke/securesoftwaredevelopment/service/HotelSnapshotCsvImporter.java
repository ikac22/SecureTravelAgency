package com.zuehlke.securesoftwaredevelopment.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class HotelSnapshotCsvImporter {
    private final DataSource dataSource;

    public HotelSnapshotCsvImporter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void restoreHotelState(int hotelId, long snapshotId, Path directory) throws IOException, SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                HotelRow hotel = readHotel(directory.resolve(HotelSnapshotCsvExporter.HOTEL_CSV), hotelId);

                deleteCurrentState(connection, hotelId);
                importRoomTypes(connection, directory.resolve(HotelSnapshotCsvExporter.ROOM_TYPES_CSV), hotelId);
                importReservations(connection, directory.resolve(HotelSnapshotCsvExporter.RESERVATIONS_CSV), hotelId);
                importRatings(connection, directory.resolve(HotelSnapshotCsvExporter.RATINGS_CSV), hotelId);
                updateHotel(connection, hotel, snapshotId);

                connection.commit();
            } catch (IOException | SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private HotelRow readHotel(Path file, int hotelId) throws IOException {
        try (CSVParser parser = open(file, "id", "cityId", "name", "description", "address")) {
            List<CSVRecord> records = parser.getRecords();
            if (records.size() != 1) {
                throw new IllegalArgumentException("Snapshot must contain exactly one hotel row");
            }

            CSVRecord row = records.get(0);
            int rowHotelId = integer(row, "id");
            if (rowHotelId != hotelId) {
                throw new IllegalArgumentException("Snapshot hotel does not match requested hotel");
            }

            return new HotelRow(
                    rowHotelId,
                    integer(row, "cityId"),
                    row.get("name"),
                    row.get("description"),
                    row.get("address")
            );
        }
    }

    private void deleteCurrentState(Connection connection, int hotelId) throws SQLException {
        delete(connection, "DELETE FROM reservation WHERE hotelId = ?", hotelId);
        delete(connection, "DELETE FROM ratings WHERE hotelId = ?", hotelId);
        delete(connection, "DELETE FROM roomType WHERE hotelId = ?", hotelId);
    }

    private void delete(Connection connection, String sql, int hotelId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, hotelId);
            statement.executeUpdate();
        }
    }

    private void importRoomTypes(Connection connection, Path file, int hotelId) throws IOException, SQLException {
        String sql = "INSERT INTO roomType(id, hotelId, name, capacity, pricePerNight, totalRooms) " +
                "VALUES(?, ?, ?, ?, ?, ?)";
        try (CSVParser parser = open(file,
                "id", "hotelId", "name", "capacity", "pricePerNight", "totalRooms");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CSVRecord row : parser) {
                requireHotel(row, hotelId);
                statement.setInt(1, integer(row, "id"));
                statement.setInt(2, hotelId);
                statement.setString(3, row.get("name"));
                statement.setInt(4, integer(row, "capacity"));
                statement.setBigDecimal(5, decimal(row, "pricePerNight"));
                statement.setInt(6, integer(row, "totalRooms"));
                statement.executeUpdate();
            }
        }
    }

    private void importReservations(Connection connection, Path file, int hotelId) throws IOException, SQLException {
        String sql = "INSERT INTO reservation(id, userId, hotelId, roomTypeId, startDate, endDate, " +
                "roomsCount, guestsCount, totalPrice) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (CSVParser parser = open(file,
                "id", "userId", "hotelId", "roomTypeId", "startDate", "endDate",
                "roomsCount", "guestsCount", "totalPrice");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CSVRecord row : parser) {
                requireHotel(row, hotelId);
                statement.setInt(1, integer(row, "id"));
                statement.setInt(2, integer(row, "userId"));
                statement.setInt(3, hotelId);
                statement.setInt(4, integer(row, "roomTypeId"));
                statement.setDate(5, Date.valueOf(row.get("startDate")));
                statement.setDate(6, Date.valueOf(row.get("endDate")));
                statement.setInt(7, integer(row, "roomsCount"));
                statement.setInt(8, integer(row, "guestsCount"));
                statement.setBigDecimal(9, decimal(row, "totalPrice"));
                statement.executeUpdate();
            }
        }
    }

    private void importRatings(Connection connection, Path file, int hotelId) throws IOException, SQLException {
        String sql = "INSERT INTO ratings(hotelId, userId, rating) VALUES(?, ?, ?)";
        try (CSVParser parser = open(file, "hotelId", "userId", "rating");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CSVRecord row : parser) {
                requireHotel(row, hotelId);
                statement.setInt(1, hotelId);
                statement.setInt(2, integer(row, "userId"));
                statement.setInt(3, integer(row, "rating"));
                statement.executeUpdate();
            }
        }
    }

    private void updateHotel(Connection connection, HotelRow hotel, long snapshotId) throws SQLException {
        String sql = "UPDATE hotel SET cityId = ?, name = ?, description = ?, address = ?, baselineSnapshotId = ? " +
                "WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, hotel.cityId);
            statement.setString(2, hotel.name);
            statement.setString(3, hotel.description);
            statement.setString(4, hotel.address);
            statement.setLong(5, snapshotId);
            statement.setInt(6, hotel.id);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Hotel does not exist: " + hotel.id);
            }
        }
    }

    private CSVParser open(Path file, String... headers) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Missing snapshot CSV: " + file.getFileName());
        }

        Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader);
        List<String> actualHeaders = new ArrayList<>(parser.getHeaderMap().keySet());
        if (!actualHeaders.equals(Arrays.asList(headers))) {
            parser.close();
            throw new IllegalArgumentException("Unexpected CSV header in " + file.getFileName());
        }
        return parser;
    }

    private void requireHotel(CSVRecord row, int hotelId) {
        if (integer(row, "hotelId") != hotelId) {
            throw new IllegalArgumentException("Snapshot row belongs to another hotel");
        }
    }

    private int integer(CSVRecord row, String column) {
        return Integer.parseInt(row.get(column));
    }

    private BigDecimal decimal(CSVRecord row, String column) {
        return new BigDecimal(row.get(column));
    }

    private static class HotelRow {
        private final int id;
        private final int cityId;
        private final String name;
        private final String description;
        private final String address;

        private HotelRow(int id, int cityId, String name, String description, String address) {
            this.id = id;
            this.cityId = cityId;
            this.name = name;
            this.description = description;
            this.address = address;
        }
    }
}
