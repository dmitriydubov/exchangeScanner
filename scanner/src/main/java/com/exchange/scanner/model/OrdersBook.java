package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders_book")
@Getter
@Setter
public class OrdersBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "coin_id", nullable = false, referencedColumnName = "id")
    private Coin coin;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exchange_id", nullable = false, referencedColumnName = "id")
    private Exchange exchange;

    @OneToMany(mappedBy = "ordersBook", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Ask> asks = new ArrayList<>();

    @OneToMany(mappedBy = "ordersBook", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Bid> bids = new ArrayList<>();
}