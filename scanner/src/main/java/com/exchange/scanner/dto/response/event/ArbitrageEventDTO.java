package com.exchange.scanner.dto.response.event;

import java.util.List;

public record ArbitrageEventDTO(String coin, String coinMarketCapLink, String coinMarketCapLogo, List<EventDataDTO> eventData) {
}
