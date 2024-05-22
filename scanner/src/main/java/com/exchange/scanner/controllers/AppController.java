package com.exchange.scanner.controllers;

import com.exchange.scanner.model.Exchange;
import com.exchange.scanner.services.AppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/app")
public class AppController {

    private final AppService appService;

    @GetMapping("/exchanges")
    public ResponseEntity<Set<Exchange>> exchanges() {
        return ResponseEntity.ok(appService.getExchanges());
    }
}
