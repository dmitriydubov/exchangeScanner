package com.exchange.scanner.dto.response;

import com.exchange.scanner.model.Coin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TradingFeeResponseDTO {

    private String exchange;

    private Coin coin;

    private BigDecimal tradingFee;
}
