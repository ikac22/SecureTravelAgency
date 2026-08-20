package com.zuehlke.securesoftwaredevelopment.service;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedWriter;
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

    private final DataSource dataSource;

    public HotelSnapshotCsvExporter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Path exportHotelState(int hotelId) throws IOException, SQLException {
        Path workspace = Files.createTempDirectory("hotel-snapshot-export-");

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

            try {
                int hotelRows = writeQueryToCsv(
                        connection,
                        workspace.resolve(HOTEL_CSV),
                        "SELECT id, cityId, name, description, address FROM hotel WHERE id = ?",
                        hotelId,
                        "id", "cityId", "name", "description", "address"
                );

                if (hotelRows != 1) {
                    throw new IllegalArgumentException("Hotel does not exist: " + hotelId);
                }

                writeQueryToCsv(
                        connection,
                        workspace.resolve(ROOM_TYPES_CSV),
                        "SELECT id, hotelId, name, capacity, pricePerNight, totalRooms " +
                                "FROM roomType WHERE hotelId = ? ORDER BY id",
                        hotelId,
                        "id", "hotelId", "name", "capacity", "pricePerNight", "totalRooms"
                );

                writeQueryToCsv(
                        connection,
                        workspace.resolve(RESERVATIONS_CSV),
                        "SELECT id, userId, hotelId, roomTypeId, startDate, endDate, roomsCount, guestsCount, totalPrice " +
                                "FROM reservation WHERE hotelId = ? ORDER BY id",
                        hotelId,
                        "id", "userId", "hotelId", "roomTypeId", "startDate", "endDate",
                        "roomsCount", "guestsCount", "totalPrice"
                );

                writeQueryToCsv(
                        connection,
                        workspace.resolve(RATINGS_CSV),
                        "SELECT hotelId, userId, rating FROM ratings WHERE hotelId = ? ORDER BY userId",
                        hotelId,
                        "hotelId", "userId", "rating"
                );

                connection.commit();
                return workspace;
            } catch (IOException | SQLException | RuntimeException e) {
                connection.rollback();
                deleteRecursively(workspace);
                throw e;
            }
        } catch (IOException | SQLException | RuntimeException e) {
            if (Files.exists(workspace)) {
                deleteRecursively(workspace);
            }
            throw e;
        }
    }

    private int writeQueryToCsv(Connection connection,
                                Path file,
                                String query,
                                int hotelId,
                                String... headers) throws SQLException, IOException {
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, hotelId);

            try (ResultSet resultSet = statement.executeQuery();
                 BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writeCsvRow(writer, Arrays.asList(headers));

                int rowCount = 0;
                while (resultSet.next()) {
                    String[] values = new String[headers.length];
                    for (int column = 0; column < headers.length; column++) {
                        values[column] = resultSet.getString(column + 1);
                    }
                    writeCsvRow(writer, Arrays.asList(values));
                    rowCount++;
                }
                return rowCount;
            }
        }
    }

    private void writeCsvRow(BufferedWriter writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(values.get(i)));
        }
        writer.newLine();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 ||
                value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new DeleteFailedException(e);
                }
            });
        } catch (DeleteFailedException e) {
            throw e.getCause();
        }
    }

    private static class DeleteFailedException extends RuntimeException {
        DeleteFailedException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
