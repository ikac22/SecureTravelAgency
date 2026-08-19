package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.Hotel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class HotelRepositoryTest {

    @Autowired
    private HotelRepository hotelRepository;

    @Test
    void searchesHotelsAndSortsResultsByRequestedColumn() throws SQLException {
        List<Hotel> hotels = hotelRepository.search("", "name");

        assertEquals(
                Arrays.asList("Acropolis Boutique Hotel", "Danube View Hotel", "Roma Centro Stay"),
                hotelNames(hotels)
        );
        assertEquals(
                Arrays.asList("Roma Centro Stay"),
                hotelNames(hotelRepository.search("Rome", "name"))
        );
    }

    @Test
    void unescapedSortIdentifierAllowsSqlClauseInjection() throws SQLException {
        List<Hotel> hotels = hotelRepository.search("", "name\" DESC --");

        assertEquals(
                Arrays.asList("Roma Centro Stay", "Danube View Hotel", "Acropolis Boutique Hotel"),
                hotelNames(hotels)
        );
    }

    private List<String> hotelNames(List<Hotel> hotels) {
        return hotels.stream()
                .map(Hotel::getName)
                .collect(Collectors.toList());
    }
}
