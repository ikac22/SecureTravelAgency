package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.service.DynamicPricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.script.ScriptException;

@RestController
public class DynamicPricingController {

    private final DynamicPricingService pricingService;

    public DynamicPricingController(DynamicPricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/api/lab/pricing/calculate")
    public ResponseEntity<Double> calculate(
            @RequestParam double basePrice,
            @RequestParam int nights,
            @RequestParam String rule
    ) throws ScriptException {
        return ResponseEntity.ok(pricingService.calculate(basePrice, nights, rule));
    }
}
