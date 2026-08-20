package com.zuehlke.securesoftwaredevelopment.service;

import com.zuehlke.securesoftwaredevelopment.repository.PricingFormulaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
    void savesValidPricingFormula() {
        String formula = "#basePrice * #nights * #roomsCount";

        service.saveFormula(formula);

        verify(repository).saveActiveFormula(formula);
    }

    @Test
    void rejectsUnknownPricingVariable() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveFormula("#basePrice * #nightCount")
        );

        assertTrue(exception.getMessage().contains("Unknown pricing variable"));
    }

    @Test
    void rejectsInvalidPricingFormulaSyntax() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveFormula("(#basePrice * #nights")
        );
    }

    @Test
    void calculatesReservationPriceUsingPersistedFormula() {
        when(repository.getActiveFormula()).thenReturn(
                "(#basePrice * #nights * #roomsCount) - (#previousReservations * 5)"
        );

        BigDecimal result = service.calculatePrice(
                new BigDecimal("100.00"),
                5,
                1,
                2,
                7
        );

        assertEquals(new BigDecimal("465.00"), result);
    }

    @Test
    void pricingFormulaCanAccessJavaRuntime() {
        when(repository.getActiveFormula()).thenReturn(
                "T(java.lang.Runtime).getRuntime().availableProcessors()"
        );

        BigDecimal result = service.calculatePrice(
                new BigDecimal("100.00"),
                3,
                1,
                2,
                0
        );

        assertTrue(result.doubleValue() > 0);
    }
}
