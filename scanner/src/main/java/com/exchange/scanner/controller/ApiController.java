package com.exchange.scanner.controller;

import com.exchange.scanner.dto.Response;
import com.exchange.scanner.service.ExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {
    private final ExchangeService exchangeService;
    private final Logger logger = LoggerFactory.getLogger(ApiController.class);

    @Autowired
    public ApiController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @GetMapping("/info")
    public ResponseEntity<Response> info() {
        return new ResponseEntity<>(exchangeService.getInfo(), HttpStatus.OK);
    }
}
