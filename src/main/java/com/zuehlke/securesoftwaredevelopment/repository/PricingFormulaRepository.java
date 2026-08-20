package com.zuehlke.securesoftwaredevelopment.repository;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class PricingFormulaRepository {

    private final DataSource dataSource;

    public PricingFormulaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String getFormulaForHotel(int hotelId) {
        String query = "SELECT formula FROM pricing_formula WHERE hotelId = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, hotelId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("formula");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load hotel pricing formula", e);
        }

        return null;
    }

    public void saveFormulaForHotel(int hotelId, String formula) {
        String update = "UPDATE pricing_formula SET formula = ? WHERE hotelId = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setString(1, formula);
            statement.setInt(2, hotelId);

            if (statement.executeUpdate() == 1) {
                return;
            }

            String insert = "INSERT INTO pricing_formula(hotelId, formula) VALUES(?, ?)";
            try (PreparedStatement insertStatement = connection.prepareStatement(insert)) {
                insertStatement.setInt(1, hotelId);
                insertStatement.setString(2, formula);
                insertStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save hotel pricing formula", e);
        }
    }
}
