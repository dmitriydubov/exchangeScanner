package com.exchange.scanner.dto.response.exchangedata.depth.coindepth;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

@Getter
@Setter

public class CoinDepthBid implements Comparable<CoinDepthBid> {

    private BigDecimal price;

    private BigDecimal volume;

    @Override
    public int compareTo(@NotNull CoinDepthBid bid) {
        if (price.compareTo(bid.price) > 0) return -1;
        if (price.compareTo(bid.price) < 0) return 1;
        return price.compareTo(bid.price);
    }
}
