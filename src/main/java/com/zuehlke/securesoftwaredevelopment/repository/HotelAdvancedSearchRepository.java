package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.Hotel;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class HotelAdvancedSearchRepository {

    private final DataSource dataSource;

    public HotelAdvancedSearchRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Intentionally vulnerable laboratory functionality.
     *
     * The textual search term is correctly bound through PreparedStatement,
     * while minimumRating is concatenated into the SQL expression. This keeps
     * parameter binding present in the query while leaving a second input able
     * to alter the SQL grammar.
     */
    public List<Hotel> search(String searchTerm, String minimumRating) throws SQLException {
        List<Hotel> hotels = new ArrayList<>();

        String sql = "SELECT h.id, h.cityId, h.name, c.name, h.description, h.address " +
                "FROM hotel h " +
                "JOIN city c ON h.cityId = c.id " +
                "LEFT JOIN ratings r ON r.hotelId = h.id " +
                "WHERE (UPPER(h.name) LIKE UPPER(?) OR UPPER(c.name) LIKE UPPER(?)) " +
                "GROUP BY h.id, h.cityId, h.name, c.name, h.description, h.address " +
                "HAVING COALESCE(AVG(r.rating), 0) >= " + minimumRating;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String pattern = "%" + searchTerm + "%";
            statement.setString(1, pattern);
            statement.setString(2, pattern);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    hotels.add(new Hotel(
                            rs.getInt(1),
                            rs.getInt(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6)
                    ));
                }
            }
        }

        return hotels;
    }
}
