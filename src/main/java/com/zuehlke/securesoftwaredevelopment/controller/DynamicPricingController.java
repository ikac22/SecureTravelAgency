package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.service.DynamicPricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DynamicPricingController {

    private final DynamicPricingService pricingService;

    public DynamicPricingController(DynamicPricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/api/lab/pricing/preview")
    public ResponseEntity<Double> preview(
            @RequestParam double basePrice,
            @RequestParam int nights,
            @RequestParam int previousReservations,
            @RequestParam String formula
    ) {
        return ResponseEntity.ok(
                pricingService.calculate(basePrice, nights, previousReservations, formula)
        );
    }
}
