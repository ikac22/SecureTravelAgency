package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.service.HotelSnapshotService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
}
