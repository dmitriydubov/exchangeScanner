package com.exchange.scanner.controllers;

import com.exchange.scanner.dto.request.UserUpdateMarketData;
import com.exchange.scanner.dto.response.ExchangeData;
import com.exchange.scanner.dto.response.event.ArbitrageEventDTO;
import com.exchange.scanner.model.ArbitrageEvent;
import com.exchange.scanner.services.AppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.concurrent.ExecutionException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/app")
public class AppController {
    private final AppService appService;

    @GetMapping("/get-exchanges")
    public ResponseEntity<ExchangeData> exchanges(@AuthenticationPrincipal UserDetails userDetails) throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(appService.getExchanges(userDetails).get());
    }

    @GetMapping("/refresh-data")
    public ResponseEntity<Set<ArbitrageEventDTO>> refreshData(@AuthenticationPrincipal UserDetails userDetails) throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(appService.getArbitrageEvents(userDetails).get());
    }

    @PostMapping("/update")
    public ResponseEntity<ExchangeData> update(@AuthenticationPrincipal UserDetails userDetails, @RequestBody UserUpdateMarketData userData) throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(appService.updateUserMarketData(userData, userDetails).get());
    }
}
