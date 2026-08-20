package com.zuehlke.securesoftwaredevelopment.repository;

import com.zuehlke.securesoftwaredevelopment.domain.Hotel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class HotelAdvancedSearchRepositoryTest {

    @Autowired
    private HotelAdvancedSearchRepository repository;

    @Test
    void filtersHotelsByMinimumAverageRating() throws SQLException {
        List<Hotel> hotels = repository.search("", "4");

        assertEquals(1, hotels.size());
        assertEquals("Danube View Hotel", hotels.get(0).getName());
    }

    @Test
    void minimumRatingCanAlterHavingClauseDespiteBoundSearchValues() throws SQLException {
        List<Hotel> hotels = repository.search("", "4 OR 1=1");

        assertEquals(3, hotels.size());
    }
}
