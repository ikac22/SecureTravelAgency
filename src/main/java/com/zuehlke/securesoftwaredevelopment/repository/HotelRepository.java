package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.domain.Hotel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HotelRepository {

    private static final Logger LOG = LoggerFactory.getLogger(HotelRepository.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(HotelRepository.class);

    private final DataSource dataSource;

    public HotelRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Hotel> getAllHotelFromCity(int cityId) {
        List<Hotel> hotelList = new ArrayList<>();
        String query = "SELECT id, name, description, address FROM hotel WHERE cityId = " + cityId;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                Integer id = rs.getInt(1);
                String name = rs.getString(2);
                String description = rs.getString(3);
                String address = rs.getString(4);

                hotelList.add(new Hotel(id, cityId, name, description, address));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return hotelList;
    }

    public List<Hotel> getAll() {
        List<Hotel> hotelList = new ArrayList<>();
        String query = "SELECT h.id, h.name, h.description, h.address, h.cityId, c.name FROM hotel as h, city as c WHERE h.cityId = c.id";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                Integer id = rs.getInt(1);
                String name = rs.getString(2);
                String description = rs.getString(3);
                String address = rs.getString(4);
                Integer cityId = rs.getInt(5);
                String cityName = rs.getString(6);
                Hotel hotel = new Hotel();
                hotel.setId(id);
                hotel.setName(name);
                hotel.setDescription(description);
                hotel.setAddress(address);
                hotel.setCityId(cityId);
                hotel.setCityName(cityName);

                hotelList.add(hotel);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return hotelList;
    }

    public Hotel get(int hotelId) {
        String query = "SELECT h.id, h.cityId, h.name, c.name, h.description, h.address FROM hotel as h, city as c WHERE h.cityId = c.id " +
                "and h.id = " + hotelId;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            if (rs.next()) {
                return crateHotelFromResultSet(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public boolean existsById(int hotelId) {
        String query = "SELECT * FROM hotel WHERE hotel.id = " + hotelId;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    public long create(Hotel hotel) {
        String query = "INSERT INTO hotel(cityId, name, description, address) VALUES(?, ?, ?, ?)";
        long id = -1;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        ) {
            statement.setInt(1, hotel.getCityId());
            statement.setString(2, hotel.getName());
            statement.setString(3, hotel.getDescription());
            statement.setString(4, hotel.getAddress());
            int rows = statement.executeUpdate();

            if (rows == 0) {
                throw new SQLException("Creating hotel failed, no rows affected.");
            }

            ResultSet generatedKeys = statement.getGeneratedKeys();
            if (generatedKeys.next()) {
                id = generatedKeys.getLong(1);
            }

            return id;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return id;
    }

    public List<Hotel> search(String searchTerm, String sortBy) throws SQLException {
        List<Hotel> destinationList = new ArrayList<>();
        String query = "SELECT DISTINCT h.id, h.cityId, h.name AS \"name\", " +
                "c.name AS \"cityName\", h.description, h.address FROM hotel h, city c" +
                " WHERE h.cityId = c.id" +
                " AND ((UPPER(h.name) like UPPER(?)" +
                " OR UPPER(c.name) like UPPER(?)))" +
                " ORDER BY \"" + sortBy + "\"";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            String searchPattern = "%" + searchTerm + "%";
            statement.setString(1, searchPattern);
            statement.setString(2, searchPattern);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    destinationList.add(crateHotelFromResultSet(rs));
                }
            }
        }
        return destinationList;
    }

    private Hotel crateHotelFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt(1);
        int cityId = rs.getInt(2);
        String name = rs.getString(3);
        String cityName = rs.getString(4);
        String description = rs.getString(5);
        String address = rs.getString(6);

        return new Hotel(id, cityId, name, cityName, description, address);
    }
}
