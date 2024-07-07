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
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/app")
public class AppController {
    private final AppService appService;

    @GetMapping("/get-exchanges")
    public ResponseEntity<Set<String>> exchanges() throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(appService.getExchanges().get());
    }

    @GetMapping("/refresh-data")
    public ResponseEntity<List<ArbitrageEvent>> refreshData(@AuthenticationPrincipal UserDetails userDetails) throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(appService.getArbitrageOpportunities(userDetails).get());
    }
}
