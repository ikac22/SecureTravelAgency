package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.service.HotelSnapshotService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HotelSnapshotControllerTest {

    @Test
    void createsSnapshotAndRedirectsBackToHotel() throws Exception {
        HotelSnapshotService snapshotService = mock(HotelSnapshotService.class);
        HotelSnapshotController controller = new HotelSnapshotController(snapshotService);

        String result = controller.createSnapshot(1);

        verify(snapshotService).createSnapshot(1);
        assertEquals("redirect:/hotels?id=1", result);
    }
}
