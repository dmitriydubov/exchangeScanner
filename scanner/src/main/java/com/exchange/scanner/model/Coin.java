package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "coins", indexes = {
        @Index(name = "idx_coin_name", columnList = "name"),
        @Index(name = "idx_coin_isBlockBySuperuser", columnList = "is_block_by_superuser")
})
public class Coin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "coin_market_cap_link")
    private String coinMarketCapLink;

    @Column(name = "logo_link")
    private String logoLink;

    @Column(name = "is_block_by_superuser")
    private Boolean isBlockBySuperuser = false;

    @Column(name = "taker_fee", nullable = false, precision = 38, scale = 8)
    private BigDecimal takerFee = new BigDecimal(0);

    @Column(name = "volume24h", nullable = false, precision = 38, scale = 8)
    private BigDecimal volume24h = new BigDecimal(0);

    @Column(name = "deposit_link", nullable = false)
    private String depositLink;

    @Column(name = "withdraw_link", nullable = false)
    private String withdrawLink;

    @Column(name = "trade_link", nullable = false)
    private String tradeLink;

    @Column(name = "is_margin_trading_allowed", nullable = false)
    private Boolean isMarginTradingAllowed = false;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
            name = "chain_coins",
            joinColumns = @JoinColumn(name = "coin_id"),
            inverseJoinColumns = @JoinColumn(name = "chain_id")
    )
    private Set<Chain> chains = new HashSet<>();

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "orders_book_id", referencedColumnName = "id")
    private OrdersBook ordersBook;

    @ManyToMany(mappedBy = "coins")
    private Set<Exchange> exchanges = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coin coin = (Coin) o;
        return Objects.equals(name, coin.name) && Objects.equals(slug, coin.slug);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, slug);
    }
}
