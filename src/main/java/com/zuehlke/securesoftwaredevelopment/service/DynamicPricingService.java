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

    public String getFormulaForHotel(int hotelId) {
        return pricingFormulaRepository.getFormulaForHotel(hotelId);
    }

    public void saveFormula(int hotelId, String pricingFormula) {
        validateFormula(pricingFormula);
        pricingFormulaRepository.saveFormulaForHotel(hotelId, pricingFormula);
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
                    createContext(new BigDecimal("100.00"), 2L, 1, 2, 0),
                    Number.class
            );
            if (result == null) {
                throw new IllegalArgumentException("Pricing formula must return a number");
            }
        } catch (EvaluationException e) {
            throw new IllegalArgumentException("Pricing formula cannot be evaluated with the available variables", e);
        }
    }

    public PricingResult calculatePrice(
            int hotelId,
            BigDecimal basePrice,
            long nights,
            int roomsCount,
            int guestsCount,
            int previousReservations
    ) {
        BigDecimal defaultPrice = calculateDefaultPrice(basePrice, nights, roomsCount);

        try {
            String pricingFormula = pricingFormulaRepository.getFormulaForHotel(hotelId);
            if (pricingFormula == null || pricingFormula.trim().isEmpty()) {
                return PricingResult.defaultPrice(defaultPrice);
            }

            Expression expression = parser.parseExpression(pricingFormula);
            Number result = expression.getValue(
                    createContext(
                            basePrice,
                            nights,
                            roomsCount,
                            guestsCount,
                            previousReservations
                    ),
                    Number.class
            );

            if (result == null) {
                return PricingResult.defaultPrice(defaultPrice);
            }

            double numericResult = result.doubleValue();
            if (Double.isNaN(numericResult) || Double.isInfinite(numericResult) || numericResult < 0) {
                return PricingResult.defaultPrice(defaultPrice);
            }

            BigDecimal calculatedPrice = result instanceof BigDecimal
                    ? ((BigDecimal) result).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(numericResult).setScale(2, RoundingMode.HALF_UP);
            return new PricingResult(
                    calculatedPrice,
                    defaultPrice,
                    true,
                    calculatedPrice.compareTo(defaultPrice) < 0
            );
        } catch (RuntimeException e) {
            return PricingResult.defaultPrice(defaultPrice);
        }
    }

    public BigDecimal calculateDefaultPrice(BigDecimal basePrice, long nights, int roomsCount) {
        return basePrice
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(roomsCount))
                .setScale(2, RoundingMode.HALF_UP);
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
            BigDecimal basePrice,
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

    public static class PricingResult {
        private final BigDecimal price;
        private final BigDecimal defaultPrice;
        private final boolean dynamicPricingApplied;
        private final boolean discountApplied;

        public PricingResult(
                BigDecimal price,
                BigDecimal defaultPrice,
                boolean dynamicPricingApplied,
                boolean discountApplied
        ) {
            this.price = price;
            this.defaultPrice = defaultPrice;
            this.dynamicPricingApplied = dynamicPricingApplied;
            this.discountApplied = discountApplied;
        }

        public static PricingResult defaultPrice(BigDecimal price) {
            return new PricingResult(price, price, false, false);
        }

        public BigDecimal getPrice() {
            return price;
        }

        public BigDecimal getDefaultPrice() {
            return defaultPrice;
        }

        public boolean isDynamicPricingApplied() {
            return dynamicPricingApplied;
        }

        public boolean isDiscountApplied() {
            return discountApplied;
        }
    }
}
