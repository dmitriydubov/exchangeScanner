package com.exchange.scanner.model;

import com.exchange.scanner.security.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "min_volume", nullable = false, precision = 38, scale = 2)
    private BigDecimal minVolume;

    @Column(name = "max_volume", nullable = false, precision = 38, scale = 2)
    private BigDecimal maxVolume;

    @Column(name = "profit_spread", nullable = false, precision = 38, scale = 2)
    private BigDecimal profitSpread;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> coins;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "markets_buy")
    private List<String> marketsBuy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "markets_sell")
    private List<String> marketsSell;
}
