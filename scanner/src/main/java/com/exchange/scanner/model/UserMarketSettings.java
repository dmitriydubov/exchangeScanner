package com.exchange.scanner.model;

import com.exchange.scanner.security.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "user_market_settings")
public class UserMarketSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @OneToOne(cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "min_volume", nullable = false)
    private Double minVolume;

    @Column(name = "max_volume", nullable = false)
    private Double maxVolume;

    @Column(name = "profit_spread", nullable = false)
    private Double profitSpread;

    @Column(name = "percent_spread", nullable = false)
    private Double percentSpread;

    @ElementCollection(fetch = FetchType.LAZY)
    private List<String> coins;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "markets_buy")
    private List<String> marketsBuy;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "markets_sell")
    private List<String> marketsSell;
}
