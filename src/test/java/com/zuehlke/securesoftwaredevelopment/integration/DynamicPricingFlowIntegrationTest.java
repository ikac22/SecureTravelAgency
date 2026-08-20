package com.zuehlke.securesoftwaredevelopment.integration;

import com.zuehlke.securesoftwaredevelopment.controller.DynamicPricingController;
import com.zuehlke.securesoftwaredevelopment.controller.ReservationController;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.HotelRepository;
import com.zuehlke.securesoftwaredevelopment.repository.PricingFormulaRepository;
import com.zuehlke.securesoftwaredevelopment.repository.ReservationRepository;
import com.zuehlke.securesoftwaredevelopment.repository.RoomRepository;
import com.zuehlke.securesoftwaredevelopment.service.DynamicPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class DynamicPricingFlowIntegrationTest {

    @Autowired
    private DynamicPricingService dynamicPricingService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PricingFormulaRepository pricingFormulaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DynamicPricingController pricingController = new DynamicPricingController(
                dynamicPricingService,
                hotelRepository
        );
        ReservationController reservationController = new ReservationController(
                reservationRepository,
                hotelRepository,
                roomRepository,
                dynamicPricingService
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(pricingController, reservationController)
                .build();
    }

    @Test
    void configuredHotelFormulaIsUsedWhenReservationIsCreated() throws Exception {
        int hotelId = 1;
        int roomTypeId = 1;
        int userId = 1;
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 4);
        String formula = "#basePrice * #nights * #roomsCount * 0.5";
        String originalFormula = pricingFormulaRepository.getFormulaForHotel(hotelId);

        User user = new User(userId, "bruce", "wayne");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user,
                user.getPassword(),
                Collections.emptyList()
        );

        try {
            mockMvc.perform(post("/pricing/formula")
                            .param("hotelId", String.valueOf(hotelId))
                            .param("formula", formula))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/pricing/formula?hotelId=1"));

            assertEquals(formula, pricingFormulaRepository.getFormulaForHotel(hotelId));

            mockMvc.perform(post("/reservations/create")
                            .param("hotelId", String.valueOf(hotelId))
                            .param("roomTypeId", String.valueOf(roomTypeId))
                            .param("startDate", startDate.toString())
                            .param("endDate", endDate.toString())
                            .param("roomsCount", "1")
                            .param("guestsCount", "2")
                            .principal(authentication))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/reservations/new/1?created=true"));

            BigDecimal storedPrice = jdbcTemplate.queryForObject(
                    "SELECT totalPrice FROM reservation " +
                            "WHERE userId = ? AND hotelId = ? AND roomTypeId = ? AND startDate = ? AND endDate = ?",
                    new Object[]{
                            userId,
                            hotelId,
                            roomTypeId,
                            Date.valueOf(startDate),
                            Date.valueOf(endDate)
                    },
                    BigDecimal.class
            );

            assertEquals(new BigDecimal("119.99"), storedPrice);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM reservation WHERE userId = ? AND hotelId = ? AND roomTypeId = ? AND startDate = ? AND endDate = ?",
                    userId,
                    hotelId,
                    roomTypeId,
                    Date.valueOf(startDate),
                    Date.valueOf(endDate)
            );

            if (originalFormula == null) {
                jdbcTemplate.update("DELETE FROM pricing_formula WHERE hotelId = ?", hotelId);
            } else {
                pricingFormulaRepository.saveFormulaForHotel(hotelId, originalFormula);
            }
        }
    }
}
