package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.domain.Hotel;
import com.zuehlke.securesoftwaredevelopment.domain.Reservation;
import com.zuehlke.securesoftwaredevelopment.domain.RoomType;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.*;
import com.zuehlke.securesoftwaredevelopment.service.DynamicPricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Controller
public class ReservationController {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationController.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(ReservationController.class);

    private final ReservationRepository reservationRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final DynamicPricingService dynamicPricingService;

    public ReservationController(
            ReservationRepository reservationRepository,
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            DynamicPricingService dynamicPricingService
    ) {
        this.reservationRepository = reservationRepository;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.dynamicPricingService = dynamicPricingService;
    }

    @GetMapping("/reservations/view")
    public String view(Model model, Authentication authentication) {
        List<Reservation> allReservations = reservationRepository.getAll();

        User user = (User) authentication.getPrincipal();
        Integer userId = user.getId();
        List<Reservation> userReservations = reservationRepository.forUser(userId);

        model.addAttribute("allReservations", allReservations);
        model.addAttribute("userReservations", userReservations);

        return "reservations";
    }

    @GetMapping("/reservations/new/{id}")
    public String showReservation(
            @PathVariable int id,
            Model model,
            @RequestParam(value = "cityInvalid", required = false) Boolean cityInvalid,
            @RequestParam(value = "countryMissing", required = false) Boolean countryMissing,
            @RequestParam(value = "cityExists", required = false) Boolean cityExists,
            @RequestParam(value = "pricingError", required = false) Boolean pricingError
    ) {
        Hotel hotel = hotelRepository.get(id);
        List<RoomType> roomTypes = roomRepository.getAllRoomTypes(id);

        model.addAttribute("id", id);
        model.addAttribute("hotel", hotel);
        model.addAttribute("roomTypes", roomTypes);
        model.addAttribute("pricingError", Boolean.TRUE.equals(pricingError));

        return "reserve-hotel";
    }

    @PostMapping("/reservations/create")
    public String createReservation(
            @RequestParam Integer hotelId,
            @RequestParam Integer roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam Integer roomsCount,
            @RequestParam Integer guestsCount,
            Authentication authentication
    ) {
        String redirectPage = "redirect:/reservations/new/" + hotelId;
        if (hotelId == null || hotelId <= 0) return redirectPage + "?createError=true";
        if (roomTypeId == null || roomTypeId <= 0) return redirectPage + "?createError=true";
        if (roomsCount == null || roomsCount <= 0) return redirectPage + "?createError=true";
        if (guestsCount == null || guestsCount <= 0) return redirectPage + "?createError=true";
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            return redirectPage + "?dateError=true";
        }

        if (!hotelRepository.existsById(hotelId)) {
            return redirectPage + "?hotelError=true";
        }

        RoomType roomType = roomRepository.findByIdAndHotelId(roomTypeId, hotelId);
        if (roomType == null) {
            return redirectPage + "?roomTypeError=true";
        }

        int maxGuests = roomType.getCapacity() * roomsCount;
        if (guestsCount > maxGuests) {
            return redirectPage + "?createError=true";
        }

        User user = (User) authentication.getPrincipal();
        Integer userId = user.getId();

        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        int previousReservations = reservationRepository.forUser(userId).size();

        BigDecimal totalPrice;
        try {
            totalPrice = dynamicPricingService.calculatePrice(
                    roomType.getPricePerNight(),
                    nights,
                    roomsCount,
                    guestsCount,
                    previousReservations
            );
        } catch (RuntimeException e) {
            LOG.error("Could not calculate reservation price", e);
            return redirectPage + "?pricingError=true";
        }

        Reservation r = new Reservation();
        r.setUserId(userId);
        r.setHotelId(hotelId);
        r.setRoomTypeId(roomTypeId);
        r.setStartDate(startDate);
        r.setEndDate(endDate);
        r.setRoomsCount(roomsCount);
        r.setGuestsCount(guestsCount);
        r.setTotalPrice(totalPrice);

        reservationRepository.create(r);

        return redirectPage + "?created=true";
    }

    @PostMapping("/reservations/delete")
    public String delete(@RequestParam Integer id) {
        reservationRepository.deleteById(id);
        return "redirect:/reservations/view";
    }
}
