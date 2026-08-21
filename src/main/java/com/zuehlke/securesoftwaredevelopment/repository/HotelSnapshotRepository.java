package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.HotelSnapshot;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HotelSnapshotRepository {
    private static final String SNAPSHOT_SELECT =
            "SELECT s.id, s.hotelId, s.fileName, s.createdAt, s.parentSnapshotId, p.fileName, " +
                    "CASE WHEN h.baselineSnapshotId = s.id THEN TRUE ELSE FALSE END " +
                    "FROM hotelSnapshot s " +
                    "JOIN hotel h ON h.id = s.hotelId " +
                    "LEFT JOIN hotelSnapshot p ON p.id = s.parentSnapshotId ";

    private final DataSource dataSource;

    public HotelSnapshotRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<HotelSnapshot> findAllForHotel(int hotelId) {
        String query = SNAPSHOT_SELECT +
                "WHERE s.hotelId = ? ORDER BY s.createdAt DESC, s.id DESC";
        List<HotelSnapshot> snapshots = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, hotelId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    snapshots.add(fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load hotel snapshots", e);
        }
        return snapshots;
    }

    public HotelSnapshot findByIdAndHotel(long snapshotId, int hotelId) {
        String query = SNAPSHOT_SELECT + "WHERE s.id = ? AND s.hotelId = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, snapshotId);
            statement.setInt(2, hotelId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? fromResultSet(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load hotel snapshot", e);
        }
    }

    public HotelSnapshot findBaselineForHotel(int hotelId) {
        String query = SNAPSHOT_SELECT +
                "WHERE s.hotelId = ? AND h.baselineSnapshotId = s.id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, hotelId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? fromResultSet(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load baseline snapshot", e);
        }
    }

    public long create(int hotelId, String fileName, LocalDateTime createdAt, Long parentSnapshotId) {
        String query = "INSERT INTO hotelSnapshot(hotelId, fileName, createdAt, parentSnapshotId) VALUES(?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, hotelId);
            statement.setString(2, fileName);
            statement.setTimestamp(3, Timestamp.valueOf(createdAt));
            if (parentSnapshotId == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, parentSnapshotId);
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("No generated snapshot id returned");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create snapshot metadata", e);
        }
    }

    public void setBaselineForHotel(int hotelId, Long snapshotId) {
        if (snapshotId == null) {
            updateHotelBaseline(hotelId, null);
            return;
        }

        String query = "UPDATE hotel SET baselineSnapshotId = ? " +
                "WHERE id = ? AND EXISTS (" +
                "SELECT 1 FROM hotelSnapshot s WHERE s.id = ? AND s.hotelId = ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, snapshotId);
            statement.setInt(2, hotelId);
            statement.setLong(3, snapshotId);
            statement.setInt(4, hotelId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Snapshot does not belong to hotel");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update hotel baseline snapshot", e);
        }
    }

    private void updateHotelBaseline(int hotelId, Long snapshotId) {
        String query = "UPDATE hotel SET baselineSnapshotId = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            if (snapshotId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, snapshotId);
            }
            statement.setInt(2, hotelId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Hotel does not exist: " + hotelId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update hotel baseline snapshot", e);
        }
    }

    private HotelSnapshot fromResultSet(ResultSet rs) throws SQLException {
        long id = rs.getLong(1);
        int hotelId = rs.getInt(2);
        String fileName = rs.getString(3);
        LocalDateTime createdAt = rs.getTimestamp(4).toLocalDateTime();
        long parentIdValue = rs.getLong(5);
        Long parentSnapshotId = rs.wasNull() ? null : parentIdValue;
        String parentFileName = rs.getString(6);
        boolean baseline = rs.getBoolean(7);
        return new HotelSnapshot(id, hotelId, fileName, createdAt, parentSnapshotId, parentFileName, baseline);
    }
}
