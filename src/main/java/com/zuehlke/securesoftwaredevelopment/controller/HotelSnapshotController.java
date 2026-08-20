package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.service.HotelSnapshotService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
@RequestMapping("/hotels/{hotelId}/snapshots")
public class HotelSnapshotController {
    private final HotelSnapshotService snapshotService;

    public HotelSnapshotController(HotelSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @PostMapping
    public String createSnapshot(@PathVariable int hotelId) throws Exception {
        snapshotService.createSnapshot(hotelId);
        return "redirect:/hotels?id=" + hotelId;
    }

    @GetMapping("/{snapshotId}/download")
    @ResponseBody
    public ResponseEntity<Resource> downloadSnapshot(@PathVariable int hotelId,
                                                     @PathVariable long snapshotId) throws IOException {
        Path archive = snapshotService.findSnapshotArchive(hotelId, snapshotId);
        if (archive == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(archive.toFile());
        String disposition = ContentDisposition.builder("attachment")
                .filename(archive.getFileName().toString())
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType("application/gzip"))
                .contentLength(Files.size(archive))
                .body(resource);
    }
}
