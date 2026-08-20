package com.zuehlke.securesoftwaredevelopment.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.script.ScriptEngineManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicPricingServiceTest {

    @Test
    void userControlledRuleChangesExecutedProgram() throws Exception {
        Assumptions.assumeTrue(new ScriptEngineManager().getEngineByName("JavaScript") != null);

        DynamicPricingService service = new DynamicPricingService();
        assertEquals(300.0, service.calculate(100.0, 3, "basePrice * nights"));
        assertEquals(800.0, service.calculate(100.0, 3, "basePrice * nights + 500"));
    }
}
