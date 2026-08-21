package com.zuehlke.securesoftwaredevelopment.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class HotelSnapshotCsvExporter {
    public static final String HOTEL_CSV = "hotel.csv";
    public static final String ROOM_TYPES_CSV = "room-types.csv";
    public static final String RESERVATIONS_CSV = "reservations.csv";
    public static final String RATINGS_CSV = "ratings.csv";

    public static final List<String> SNAPSHOT_FILES = Collections.unmodifiableList(Arrays.asList(
            HOTEL_CSV,
            ROOM_TYPES_CSV,
            RESERVATIONS_CSV,
            RATINGS_CSV
    ));

    private static final List<ExportDefinition> EXPORTS = Arrays.asList(
            new ExportDefinition(
                    HOTEL_CSV,
                    "SELECT id, cityId, name, description, address FROM hotel WHERE id = ?",
                    "id", "cityId", "name", "description", "address"
            ),
            new ExportDefinition(
                    ROOM_TYPES_CSV,
                    "SELECT id, hotelId, name, capacity, pricePerNight, totalRooms " +
                            "FROM roomType WHERE hotelId = ? ORDER BY id",
                    "id", "hotelId", "name", "capacity", "pricePerNight", "totalRooms"
            ),
            new ExportDefinition(
                    RESERVATIONS_CSV,
                    "SELECT id, userId, hotelId, roomTypeId, startDate, endDate, roomsCount, guestsCount, totalPrice " +
                            "FROM reservation WHERE hotelId = ? ORDER BY id",
                    "id", "userId", "hotelId", "roomTypeId", "startDate", "endDate",
                    "roomsCount", "guestsCount", "totalPrice"
            ),
            new ExportDefinition(
                    RATINGS_CSV,
                    "SELECT hotelId, userId, rating FROM ratings WHERE hotelId = ? ORDER BY userId",
                    "hotelId", "userId", "rating"
            )
    );

    private final DataSource dataSource;

    public HotelSnapshotCsvExporter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void exportHotelState(int hotelId, Path directory) throws IOException, SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);

            try {
                if (!hotelExists(connection, hotelId)) {
                    throw new IllegalArgumentException("Hotel does not exist: " + hotelId);
                }

                for (ExportDefinition export : EXPORTS) {
                    export(connection, hotelId, directory, export);
                }

                connection.commit();
            } catch (IOException | SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private boolean hotelExists(Connection connection, int hotelId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM hotel WHERE id = ?")) {
            statement.setInt(1, hotelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void export(Connection connection,
                        int hotelId,
                        Path directory,
                        ExportDefinition export) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(export.query)) {
            statement.setInt(1, hotelId);

            try (ResultSet resultSet = statement.executeQuery();
                 CSVPrinter printer = new CSVPrinter(
                         Files.newBufferedWriter(directory.resolve(export.fileName), StandardCharsets.UTF_8),
                         CSVFormat.DEFAULT.withHeader(export.headers))) {
                printer.printRecords(resultSet);
            }
        }
    }

    private static class ExportDefinition {
        private final String fileName;
        private final String query;
        private final String[] headers;

        private ExportDefinition(String fileName, String query, String... headers) {
            this.fileName = fileName;
            this.query = query;
            this.headers = headers;
        }
    }
}
