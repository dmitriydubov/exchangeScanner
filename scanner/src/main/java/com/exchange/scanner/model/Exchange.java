package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.Set;


@Entity
@Getter
@Setter
@Table(name = "exchanges")
public class Exchange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "deposit_link", nullable = false)
    private String depositLink;

    @Column(name = "withdraw_link", nullable = false)
    private String withdrawLink;

    @Column(name = "trade_link", nullable = false)
    private String tradeLink;

    @Column(name = "is_block_by_superuser")
    private Boolean isBlockBySuperuser = false;

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(
            name = "exchanges_coins",
            joinColumns = @JoinColumn(name = "exchange_id"),
            inverseJoinColumns = @JoinColumn(name = "coin_id")
    )
    @BatchSize(size = 1000)
    private Set<Coin> coins = new HashSet<>();
}
