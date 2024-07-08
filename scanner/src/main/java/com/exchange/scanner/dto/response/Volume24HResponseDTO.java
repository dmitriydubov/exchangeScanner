package com.exchange.scanner.dto.response;

import com.exchange.scanner.model.Coin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Volume24HResponseDTO {

    private String exchange;

    private Coin coin;

    private BigDecimal volume24H;
}
