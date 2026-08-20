package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.Hotel;
import com.zuehlke.securesoftwaredevelopment.repository.HotelRepository;
import com.zuehlke.securesoftwaredevelopment.service.DynamicPricingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/pricing")
@PreAuthorize("hasAuthority('PRICING_MANAGE')")
public class DynamicPricingController {

    private final DynamicPricingService pricingService;
    private final HotelRepository hotelRepository;

    public DynamicPricingController(
            DynamicPricingService pricingService,
            HotelRepository hotelRepository
    ) {
        this.pricingService = pricingService;
        this.hotelRepository = hotelRepository;
    }

    @GetMapping("/formula")
    public String showFormula(
            @RequestParam(value = "hotelId", required = false) Integer hotelId,
            Model model
    ) {
        List<Hotel> hotels = hotelRepository.getAll();
        model.addAttribute("hotels", hotels);
        model.addAttribute("availableVariables", pricingService.getAvailableVariables());

        if (hotels.isEmpty()) {
            model.addAttribute("selectedHotel", null);
            model.addAttribute("formula", "");
            model.addAttribute("hasFormula", false);
            return "pricing-formula";
        }

        Hotel selectedHotel = hotelId == null ? null : hotelRepository.get(hotelId);
        if (selectedHotel == null) {
            selectedHotel = hotels.get(0);
        }

        String storedFormula = pricingService.getFormulaForHotel(selectedHotel.getId());

        model.addAttribute("selectedHotel", selectedHotel);
        model.addAttribute("hasFormula", storedFormula != null);
        if (!model.containsAttribute("formula")) {
            model.addAttribute("formula", storedFormula == null ? "" : storedFormula);
        }

        return "pricing-formula";
    }

    @PostMapping("/formula")
    public String saveFormula(
            @RequestParam Integer hotelId,
            @RequestParam String formula,
            RedirectAttributes redirectAttributes
    ) {
        Hotel hotel = hotelRepository.get(hotelId);
        if (hotel == null) {
            redirectAttributes.addFlashAttribute("error", "Hotel does not exist");
            return "redirect:/pricing/formula";
        }

        try {
            pricingService.saveFormula(hotelId, formula);
            redirectAttributes.addFlashAttribute("saved", true);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("formula", formula);
        }

        redirectAttributes.addAttribute("hotelId", hotelId);
        return "redirect:/pricing/formula";
    }
}
