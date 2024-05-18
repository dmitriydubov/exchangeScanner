package com.exchange.scanner.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppController {

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
    public String dashboardPage() {
        return "dashboard";
    }
}
