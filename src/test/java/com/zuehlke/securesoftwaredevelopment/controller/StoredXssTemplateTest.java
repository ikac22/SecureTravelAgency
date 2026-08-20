package com.zuehlke.securesoftwaredevelopment.controller;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredXssTemplateTest {

    @Test
    void storedDescriptionIsInsertedIntoDomAsHtml() throws Exception {
        String template = new String(
                Files.readAllBytes(Paths.get("src/main/resources/templates/hotel.html")),
                StandardCharsets.UTF_8
        );

        assertTrue(template.contains("fetch('/api/hotels/' + hotelId + '/description')"));
        assertTrue(template.contains("insertAdjacentHTML('beforeend', hotelData.description)"));
        assertFalse(template.contains("th:utext=\"${hotel.description}\""));
    }
}
