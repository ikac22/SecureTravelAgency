package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.service.PartnerContentPreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class PartnerContentPreviewController {

    private final PartnerContentPreviewService previewService;

    public PartnerContentPreviewController(PartnerContentPreviewService previewService) {
        this.previewService = previewService;
    }

    @GetMapping("/api/lab/partners/preview")
    public ResponseEntity<String> preview(@RequestParam String url) throws IOException {
        return ResponseEntity.ok(previewService.fetch(url));
    }
}
