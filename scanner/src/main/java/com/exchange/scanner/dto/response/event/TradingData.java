package com.exchange.scanner.dto.response.event;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradingData {

    private BigDecimal volume24hAsk;

    private BigDecimal volume24hBid;

    private BigDecimal tradingFeeAsk;

    private BigDecimal tradingFeeBid;

    private String chainName;

    private BigDecimal chainFeeAmount;

    private Boolean isWarning;

    private String slug;
}
