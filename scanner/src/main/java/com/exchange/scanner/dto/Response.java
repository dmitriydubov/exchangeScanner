package com.exchange.scanner.dto;

import java.util.List;

public record Response(boolean result, String message, List<CoinData> coins) {}
