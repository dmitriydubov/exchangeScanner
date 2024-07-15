package com.exchange.scanner.dto.response.event;

import lombok.*;

import java.util.List;
import java.util.Objects;

@Getter
@Setter
public class ArbitrageEvent {

    private String coin;

    private String coinMarketCapLink;

    private String coinMarketCapLogo;

    private List<EventData> eventData;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArbitrageEvent that = (ArbitrageEvent) o;
        return Objects.equals(coin, that.coin);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(coin);
    }
}
