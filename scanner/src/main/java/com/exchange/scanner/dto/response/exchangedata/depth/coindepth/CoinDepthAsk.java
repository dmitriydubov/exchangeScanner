package com.exchange.scanner.dto.response.exchangedata.depth.coindepth;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

@Getter
@Setter
public class CoinDepthAsk implements Comparable<CoinDepthAsk> {

    private BigDecimal price;

    private BigDecimal volume;

    @Override
    public int compareTo(@NotNull CoinDepthAsk ask) {
        return price.compareTo(ask.price);
    }
}
