package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.repository.PricingFormulaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicPricingServiceTest {

    private PricingFormulaRepository repository;
    private DynamicPricingService service;

    @BeforeEach
    void setUp() {
        repository = mock(PricingFormulaRepository.class);
        service = new DynamicPricingService(repository);
    }

    @Test
    void savesValidPricingFormulaForHotel() {
        String formula = "#basePrice * #nights * #roomsCount";

        service.saveFormula(2, formula);

        verify(repository).saveFormulaForHotel(2, formula);
    }

    @Test
    void rejectsUnknownPricingVariable() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveFormula(1, "#basePrice * #nightCount")
        );

        assertTrue(exception.getMessage().contains("Unknown pricing variable"));
    }

    @Test
    void rejectsInvalidPricingFormulaSyntax() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveFormula(1, "(#basePrice * #nights")
        );
    }

    @Test
    void hotelWithoutPricingFormulaUsesDefaultPrice() {
        when(repository.getFormulaForHotel(2)).thenReturn(null);

        DynamicPricingService.PricingResult result = service.calculatePrice(
                2,
                new BigDecimal("100.00"),
                3,
                2,
                4,
                0
        );

        assertEquals(new BigDecimal("600.00"), result.getPrice());
        assertEquals(new BigDecimal("600.00"), result.getDefaultPrice());
        assertFalse(result.isDynamicPricingApplied());
        assertFalse(result.isDiscountApplied());
    }

    @Test
    void hotelPricingFormulaCanApplyDiscount() {
        when(repository.getFormulaForHotel(1)).thenReturn(
                "#basePrice * #nights * #roomsCount * 0.90"
        );

        DynamicPricingService.PricingResult result = service.calculatePrice(
                1,
                new BigDecimal("100.00"),
                3,
                2,
                4,
                0
        );

        assertEquals(new BigDecimal("540.00"), result.getPrice());
        assertEquals(new BigDecimal("600.00"), result.getDefaultPrice());
        assertTrue(result.isDynamicPricingApplied());
        assertTrue(result.isDiscountApplied());
    }

    @Test
    void failedHotelPricingFallsBackToDefaultWithoutRetry() {
        when(repository.getFormulaForHotel(3)).thenReturn("(#basePrice *");

        DynamicPricingService.PricingResult result = service.calculatePrice(
                3,
                new BigDecimal("80.00"),
                4,
                1,
                2,
                0
        );

        assertEquals(new BigDecimal("320.00"), result.getPrice());
        assertFalse(result.isDynamicPricingApplied());
        assertFalse(result.isDiscountApplied());
        verify(repository, times(1)).getFormulaForHotel(3);
    }

    @Test
    void pricingFormulaCanAccessJavaRuntime() {
        when(repository.getFormulaForHotel(1)).thenReturn(
                "T(java.lang.Runtime).getRuntime().availableProcessors()"
        );

        DynamicPricingService.PricingResult result = service.calculatePrice(
                1,
                new BigDecimal("100.00"),
                3,
                1,
                2,
                0
        );

        assertTrue(result.getPrice().doubleValue() > 0);
        assertTrue(result.isDynamicPricingApplied());
    }
}
