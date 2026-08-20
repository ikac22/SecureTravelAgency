package com.zuehlke.securesoftwaredevelopment.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class HotelSnapshotCsvExporterTest {

    @Autowired
    private HotelSnapshotCsvExporter exporter;

    @Test
    void exportsOnlySelectedHotelStateIntoSeparateCsvFiles() throws Exception {
        Path workspace = exporter.exportHotelState(1);
        try {
            assertEquals(HotelSnapshotCsvExporter.SNAPSHOT_FILES.size(), Files.list(workspace).count());

            List<String> hotel = Files.readAllLines(workspace.resolve(HotelSnapshotCsvExporter.HOTEL_CSV));
            assertEquals("id,cityId,name,description,address", hotel.get(0));
            assertEquals("1,1,Danube View Hotel,Modern hotel near the river promenade.,\"Cara Urosa 10, Belgrade\"", hotel.get(1));
            assertEquals(2, hotel.size());

            List<String> roomTypes = Files.readAllLines(workspace.resolve(HotelSnapshotCsvExporter.ROOM_TYPES_CSV));
            assertEquals("id,hotelId,name,capacity,pricePerNight,totalRooms", roomTypes.get(0));
            assertEquals(3, roomTypes.size());
            assertTrue(roomTypes.stream().anyMatch(line -> line.contains("Standard Double")));
            assertTrue(roomTypes.stream().anyMatch(line -> line.contains("Family Suite")));
            assertFalse(roomTypes.stream().anyMatch(line -> line.contains("Economy Single")));

            List<String> reservations = Files.readAllLines(workspace.resolve(HotelSnapshotCsvExporter.RESERVATIONS_CSV));
            assertEquals("id,userId,hotelId,roomTypeId,startDate,endDate,roomsCount,guestsCount,totalPrice", reservations.get(0));
            assertEquals(2, reservations.size());
            assertTrue(reservations.get(1).contains(",1,1,1,2026-03-10,2026-03-13,"));

            List<String> ratings = Files.readAllLines(workspace.resolve(HotelSnapshotCsvExporter.RATINGS_CSV));
            assertEquals("hotelId,userId,rating", ratings.get(0));
            assertEquals(4, ratings.size());
            assertEquals("1,1,5", ratings.get(1));
            assertEquals("1,2,4", ratings.get(2));
            assertEquals("1,3,5", ratings.get(3));
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    void rejectsUnknownHotel() {
        assertThrows(IllegalArgumentException.class, () -> exporter.exportHotelState(99999));
    }

    private void deleteRecursively(Path root) throws Exception {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
