package com.exchange.scanner.controller;

import com.exchange.scanner.dto.ErrorResponse;
import com.exchange.scanner.dto.MarketSettings;
import com.exchange.scanner.dto.Response;
import com.exchange.scanner.service.ExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

@RestController
public class ApiController {
    private final ExchangeService exchangeService;
    private final Logger logger = LoggerFactory.getLogger(ApiController.class);

    @Autowired
    public ApiController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @GetMapping("/info")
    public ResponseEntity<?> info(@RequestBody MarketSettings marketSettings) {
        Response response;
        try {
            response = exchangeService.getInfo(marketSettings);
            logger.info("Данные по монетам:{}", response.coins() + ". Успешно обновлены");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (HttpClientErrorException ex) {
            ErrorResponse errorResponse = new ErrorResponse(false, ex.getLocalizedMessage());
            logger.error("Ошибка получения данных. Код ошибки: {}", ex.getStatusCode());
            return new ResponseEntity<>(errorResponse, ex.getStatusCode());
        }
    }
}
