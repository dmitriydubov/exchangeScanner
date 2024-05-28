package com.exchange.scanner.controllers;

import com.exchange.scanner.dto.response.SimpleResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @PostMapping("/update-all-coin-list")
    public ResponseEntity<SimpleResponse> updateAllCoinList() {
        return ResponseEntity.ok(new SimpleResponse("Обновление списка валют завершено"));
    }
}
