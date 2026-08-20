package com.zuehlke.securesoftwaredevelopment.service;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

@Service
public class DynamicPricingService {

    private final ExpressionParser parser = new SpelExpressionParser();

    public double calculate(
            double basePrice,
            int nights,
            int previousReservations,
            String pricingFormula
    ) {
        if (pricingFormula == null || pricingFormula.length() > 200) {
            throw new IllegalArgumentException("Invalid pricing formula");
        }

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("basePrice", basePrice);
        context.setVariable("nights", nights);
        context.setVariable("previousReservations", previousReservations);

        Expression expression = parser.parseExpression(pricingFormula);
        Number result = expression.getValue(context, Number.class);
        if (result == null) {
            throw new IllegalArgumentException("Pricing formula must return a number");
        }

        return result.doubleValue();
    }
}
