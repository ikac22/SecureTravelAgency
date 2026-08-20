package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.service.HotelSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotelSnapshotControllerTest {

    @Test
    void createsSnapshotAndRedirectsBackToHotel() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);

        String result = controller.createSnapshot(1);

        verify(snapshotService).createSnapshot(1);
        assertEquals("redirect:/hotels?id=1", result);
    }

    @Test
    void downloadsExistingSnapshotArchive() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        Path archive = Files.createTempFile("hotel-1-snapshot-", ".tar.gz");
        Files.write(archive, "snapshot".getBytes(StandardCharsets.UTF_8));
        try {
            when(snapshotService.findSnapshotArchive(1, 7L)).thenReturn(archive);

            ResponseEntity<Resource> response = controller.downloadSnapshot(1, 7L);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("application/gzip", response.getHeaders().getContentType().toString());
            assertEquals(Files.size(archive), response.getHeaders().getContentLength());
            assertTrue(response.getHeaders().getFirst("Content-Disposition").contains(archive.getFileName().toString()));
            assertNotNull(response.getBody());
            assertEquals(archive.toFile(), response.getBody().getFile());
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void returnsNotFoundForMissingSnapshotArchive() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        when(snapshotService.findSnapshotArchive(1, 99L)).thenReturn(null);

        ResponseEntity<Resource> response = controller.downloadSnapshot(1, 99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
