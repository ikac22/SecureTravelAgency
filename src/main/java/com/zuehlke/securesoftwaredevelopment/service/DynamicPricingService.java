package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.repository.PricingFormulaRepository;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DynamicPricingService {

    private static final int MAX_FORMULA_LENGTH = 500;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("#([A-Za-z_][A-Za-z0-9_]*)");
    private static final Set<String> ALLOWED_VARIABLES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "basePrice",
                    "nights",
                    "roomsCount",
                    "guestsCount",
                    "previousReservations"
            ))
    );

    private final ExpressionParser parser = new SpelExpressionParser();
    private final PricingFormulaRepository pricingFormulaRepository;

    public DynamicPricingService(PricingFormulaRepository pricingFormulaRepository) {
        this.pricingFormulaRepository = pricingFormulaRepository;
    }

    public Set<String> getAvailableVariables() {
        return ALLOWED_VARIABLES;
    }

    public String getActiveFormula() {
        return pricingFormulaRepository.getActiveFormula();
    }

    public void saveFormula(String pricingFormula) {
        validateFormula(pricingFormula);
        pricingFormulaRepository.saveActiveFormula(pricingFormula);
    }

    public void validateFormula(String pricingFormula) {
        if (pricingFormula == null || pricingFormula.trim().isEmpty()) {
            throw new IllegalArgumentException("Pricing formula is required");
        }

        if (pricingFormula.length() > MAX_FORMULA_LENGTH) {
            throw new IllegalArgumentException("Pricing formula is too long");
        }

        validateVariableNames(pricingFormula);

        Expression expression;
        try {
            expression = parser.parseExpression(pricingFormula);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Pricing formula has invalid syntax", e);
        }

        try {
            Number result = expression.getValue(
                    createContext(100.0, 2L, 1, 2, 0),
                    Number.class
            );
            if (result == null) {
                throw new IllegalArgumentException("Pricing formula must return a number");
            }
        } catch (EvaluationException e) {
            throw new IllegalArgumentException("Pricing formula cannot be evaluated with the available variables", e);
        }
    }

    public BigDecimal calculatePrice(
            BigDecimal basePrice,
            long nights,
            int roomsCount,
            int guestsCount,
            int previousReservations
    ) {
        String pricingFormula = getActiveFormula();
        Expression expression = parser.parseExpression(pricingFormula);

        Number result = expression.getValue(
                createContext(
                        basePrice.doubleValue(),
                        nights,
                        roomsCount,
                        guestsCount,
                        previousReservations
                ),
                Number.class
        );

        if (result == null) {
            throw new IllegalStateException("Pricing formula did not return a numeric value");
        }

        double numericResult = result.doubleValue();
        if (Double.isNaN(numericResult) || Double.isInfinite(numericResult) || numericResult < 0) {
            throw new IllegalStateException("Pricing formula returned an invalid price");
        }

        return BigDecimal.valueOf(numericResult).setScale(2, RoundingMode.HALF_UP);
    }

    private void validateVariableNames(String pricingFormula) {
        Matcher matcher = VARIABLE_PATTERN.matcher(pricingFormula);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (!ALLOWED_VARIABLES.contains(variableName)) {
                throw new IllegalArgumentException("Unknown pricing variable: #" + variableName);
            }
        }
    }

    private StandardEvaluationContext createContext(
            double basePrice,
            long nights,
            int roomsCount,
            int guestsCount,
            int previousReservations
    ) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("basePrice", basePrice);
        context.setVariable("nights", nights);
        context.setVariable("roomsCount", roomsCount);
        context.setVariable("guestsCount", guestsCount);
        context.setVariable("previousReservations", previousReservations);
        return context;
    }
}
