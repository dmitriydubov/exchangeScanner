package com.exchange.scanner.dto.response.event;

import lombok.*;

import java.util.Map;

@Getter
@Setter
public class ArbitrageOpportunity {

    private String coinName;

    private String coinMarketCapLink;

    private String coinMarketCapLogo;

    private Map<String, EventData> tradingData;
}
