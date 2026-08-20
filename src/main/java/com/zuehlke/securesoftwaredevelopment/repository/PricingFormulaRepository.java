package com.zuehlke.securesoftwaredevelopment.repository;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class PricingFormulaRepository {

    private static final int ACTIVE_FORMULA_ID = 1;

    private final DataSource dataSource;

    public PricingFormulaRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String getActiveFormula() {
        String query = "SELECT formula FROM pricing_formula WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, ACTIVE_FORMULA_ID);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("formula");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not load pricing formula", e);
        }

        throw new IllegalStateException("Active pricing formula is not configured");
    }

    public void saveActiveFormula(String formula) {
        String query = "UPDATE pricing_formula SET formula = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, formula);
            statement.setInt(2, ACTIVE_FORMULA_ID);

            int updatedRows = statement.executeUpdate();
            if (updatedRows != 1) {
                throw new IllegalStateException("Active pricing formula is not configured");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save pricing formula", e);
        }
    }
}
