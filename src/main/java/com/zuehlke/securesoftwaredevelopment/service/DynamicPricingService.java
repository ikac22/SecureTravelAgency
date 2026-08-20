package com.zuehlke.securesoftwaredevelopment.service;

import org.springframework.stereotype.Service;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

@Service
public class DynamicPricingService {

    public double calculate(double basePrice, int nights, String pricingRule) throws ScriptException {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
        if (engine == null) {
            throw new IllegalStateException("JavaScript engine is not available");
        }

        engine.put("basePrice", basePrice);
        engine.put("nights", nights);

        Object result = engine.eval(pricingRule);
        if (!(result instanceof Number)) {
            throw new IllegalArgumentException("Pricing rule must return a number");
        }

        return ((Number) result).doubleValue();
    }
}
