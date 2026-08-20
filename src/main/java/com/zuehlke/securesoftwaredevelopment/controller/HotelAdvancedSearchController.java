package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.Hotel;
import com.zuehlke.securesoftwaredevelopment.repository.HotelAdvancedSearchRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping("/api/lab/hotels")
public class HotelAdvancedSearchController {

    private final HotelAdvancedSearchRepository repository;

    public HotelAdvancedSearchController(HotelAdvancedSearchRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/advanced-search")
    public ResponseEntity<List<Hotel>> advancedSearch(
            @RequestParam String query,
            @RequestParam String minimumRating
    ) throws SQLException {
        return ResponseEntity.ok(repository.search(query, minimumRating));
    }
}
