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
@Table(name = "coins")
public class Coin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String symbol;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_block_by_superuser")
    private Boolean isBlockBySuperuser = false;

    @Column(name = "taker_fee", nullable = false, precision = 38, scale = 4)
    private BigDecimal takerFee = new BigDecimal(0);

    @Column(name = "volume24h", nullable = false, precision = 38)
    private BigDecimal volume24h = new BigDecimal(0);

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinTable(
            name = "chain_coins",
            joinColumns = @JoinColumn(name = "coin_id"),
            inverseJoinColumns = @JoinColumn(name = "chain_id")
    )
    private Set<Chain> chains = new HashSet<>();

    @ManyToMany(mappedBy = "coins", cascade = CascadeType.ALL)
    private Set<Exchange> exchanges = new HashSet<>();

    @Transient
    @OneToMany(mappedBy = "coin", cascade = CascadeType.ALL)
    private Set<OrdersBook> ordersBooks = new HashSet<>();
}
