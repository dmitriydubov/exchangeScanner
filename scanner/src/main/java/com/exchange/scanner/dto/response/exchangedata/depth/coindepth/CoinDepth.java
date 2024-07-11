package com.exchange.scanner.dto.response.exchangedata.depth.coindepth;

import com.exchange.scanner.model.Coin;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.TreeSet;

/**
 * @CoinDepth - dto класс объектов с информацией о стаканх цен (depth) по каждой монете
 * **/

@Getter
@Setter
public class CoinDepth {

    private Integer statusCode;

    private String exchange;

    private Coin coin;

    private String slug;

    private TreeSet<CoinDepthBid> coinDepthBids;

    private TreeSet<CoinDepthAsk> coinDepthAsks;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoinDepth coinDepth = (CoinDepth) o;
        return Objects.equals(slug, coinDepth.slug);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(slug);
    }
}
