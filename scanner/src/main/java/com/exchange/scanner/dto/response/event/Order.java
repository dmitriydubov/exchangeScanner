package com.exchange.scanner.dto.response.event;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class Order {

    private String type;

    private BigDecimal price;

    private BigDecimal volume;
}
