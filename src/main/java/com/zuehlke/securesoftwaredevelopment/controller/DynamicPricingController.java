package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.service.DynamicPricingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/pricing")
@PreAuthorize("hasAuthority('PRICING_MANAGE')")
public class DynamicPricingController {

    private final DynamicPricingService pricingService;

    public DynamicPricingController(DynamicPricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/formula")
    public String showFormula(Model model) {
        if (!model.containsAttribute("formula")) {
            model.addAttribute("formula", pricingService.getActiveFormula());
        }
        model.addAttribute("availableVariables", pricingService.getAvailableVariables());
        return "pricing-formula";
    }

    @PostMapping("/formula")
    public String saveFormula(
            @RequestParam String formula,
            RedirectAttributes redirectAttributes
    ) {
        try {
            pricingService.saveFormula(formula);
            redirectAttributes.addFlashAttribute("saved", true);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("formula", formula);
        }

        return "redirect:/pricing/formula";
    }
}
