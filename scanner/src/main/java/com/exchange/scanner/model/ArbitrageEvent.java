package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "arbitrage_events")
public class ArbitrageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String coin;

    @Column(name = "coin_market_cap_link")
    private String coinMarketCapLink;

    @Column(name = "coin_market_cap_logo")
    private String coinMarketCapLogo;

    @OneToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE}, fetch = FetchType.EAGER)
    @JoinColumn(name = "arbitrage_event_id")
    private Set<EventData> eventData;

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
