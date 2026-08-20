package com.zuehlke.securesoftwaredevelopment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicPricingServiceTest {

    private final DynamicPricingService service = new DynamicPricingService();

    @Test
    void evaluatesLegitimatePricingFormula() {
        double result = service.calculate(
                100.0,
                3,
                2,
                "(#basePrice * #nights) - (#previousReservations * 5)"
        );

        assertEquals(290.0, result);
    }

    @Test
    void pricingFormulaCanAccessJavaRuntime() {
        double result = service.calculate(
                100.0,
                3,
                2,
                "T(java.lang.Runtime).getRuntime().availableProcessors()"
        );

        assertTrue(result > 0);
    }
}
