package com.exchange.scanner.controllers;

import com.exchange.scanner.dto.response.event.ArbitrageEvent;
import com.exchange.scanner.services.AppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/app")
public class AppController {
    private final AppService appService;

    @GetMapping("/get-exchanges")
    public ResponseEntity<Set<String>> exchanges() {
        return ResponseEntity.ok(appService.getExchanges());
    }

    @GetMapping("/refresh-data")
    public ResponseEntity<List<ArbitrageEvent>> refreshData(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appService.getArbitrageOpportunities(userDetails));
    }
}
