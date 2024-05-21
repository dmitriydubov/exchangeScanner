package com.exchange.scanner.controllers;

import com.exchange.scanner.services.AppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;

    @GetMapping("/")
    public String mainPage() {
        return "index";
    }

    @GetMapping("/subscribe")
    public String subscribePage() {
        return "subscribe";
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('USER')")
    public String dashboardPage(Model model) {
        model.addAttribute("exchangesList", appService.getExchanges());
        return "dashboard";
    }
}
