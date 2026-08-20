package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.HotelSnapshot;
import com.zuehlke.securesoftwaredevelopment.service.HotelSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotelSnapshotControllerTest {

    @Test
    void createsSnapshotAndRedirectsBackToHotel() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        RedirectAttributes attributes = new RedirectAttributesModelMap();

        String result = controller.createSnapshot(1, attributes);

        verify(snapshotService).createSnapshot(1);
        assertEquals("redirect:/hotels?id=1", result);
        assertEquals("Snapshot created successfully.", attributes.getFlashAttributes().get("snapshotSuccess"));
        assertNull(attributes.getFlashAttributes().get("snapshotError"));
    }

    @Test
    void reportsSnapshotCreationFailureWithoutExposingException() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        RedirectAttributes attributes = new RedirectAttributesModelMap();
        doThrow(new IOException("tar: internal command details"))
                .when(snapshotService).createSnapshot(1);

        String result = controller.createSnapshot(1, attributes);

        assertEquals("redirect:/hotels?id=1", result);
        assertEquals("Could not create snapshot. Please try again.",
                attributes.getFlashAttributes().get("snapshotError"));
        assertNull(attributes.getFlashAttributes().get("snapshotSuccess"));
    }

    @Test
    void rollsBackSnapshotAndRedirectsBackToHotel() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        RedirectAttributes attributes = new RedirectAttributesModelMap();
        HotelSnapshot snapshot = mock(HotelSnapshot.class);
        when(snapshotService.rollbackToSnapshot(1, 7L)).thenReturn(snapshot);

        String result = controller.rollbackSnapshot(1, 7L, attributes);

        verify(snapshotService).rollbackToSnapshot(1, 7L);
        assertEquals("redirect:/hotels?id=1", result);
        assertEquals("Snapshot restored successfully.", attributes.getFlashAttributes().get("snapshotSuccess"));
        assertNull(attributes.getFlashAttributes().get("snapshotError"));
    }

    @Test
    void reportsRollbackFailureWithoutExposingException() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        RedirectAttributes attributes = new RedirectAttributesModelMap();
        when(snapshotService.rollbackToSnapshot(1, 7L))
                .thenThrow(new IOException("malformed archive details"));

        String result = controller.rollbackSnapshot(1, 7L, attributes);

        assertEquals("redirect:/hotels?id=1", result);
        assertEquals("Could not restore snapshot. The current hotel state was not changed.",
                attributes.getFlashAttributes().get("snapshotError"));
        assertNull(attributes.getFlashAttributes().get("snapshotSuccess"));
    }

    @Test
    void returnsNotFoundWhenRollbackSnapshotDoesNotExist() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        RedirectAttributes attributes = new RedirectAttributesModelMap();
        when(snapshotService.rollbackToSnapshot(1, 99L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.rollbackSnapshot(1, 99L, attributes)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
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

    @Test
    void downloadsSelectedSnapshotFiles() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        List<String> selection = Arrays.asList("hotel.csv", "ratings.csv");
        byte[] archive = "selected snapshot".getBytes(StandardCharsets.UTF_8);
        when(snapshotService.createSelectiveArchive(1, 7L, selection)).thenReturn(archive);

        ResponseEntity<byte[]> response = controller.downloadSelectedSnapshot(1, 7L, selection);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/gzip", response.getHeaders().getContentType().toString());
        assertEquals(archive.length, response.getHeaders().getContentLength());
        assertTrue(response.getHeaders().getFirst("Content-Disposition")
                .contains("hotel-1-snapshot-7-selected.tar.gz"));
        assertArrayEquals(archive, response.getBody());
    }

    @Test
    void rejectsInvalidSelectedSnapshotFiles() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        List<String> selection = Arrays.asList("../hotel.csv");
        when(snapshotService.createSelectiveArchive(1, 7L, selection))
                .thenThrow(new IllegalArgumentException("Invalid snapshot file selection"));

        ResponseEntity<byte[]> response = controller.downloadSelectedSnapshot(1, 7L, selection);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void returnsNotFoundForSelectiveDownloadOfMissingSnapshot() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);
        List<String> selection = Arrays.asList("hotel.csv");
        when(snapshotService.createSelectiveArchive(1, 99L, selection)).thenReturn(null);

        ResponseEntity<byte[]> response = controller.downloadSelectedSnapshot(1, 99L, selection);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
