package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

    @ManyToOne(cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "coin_id", nullable = false, referencedColumnName = "id")
    private Coin coin;

    @ManyToOne
    @JoinColumn(name = "exchange_id", nullable = false, referencedColumnName = "id")
    private Exchange exchange;

    @OneToMany(mappedBy = "ordersBook", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Ask> asks = new ArrayList<>();

    @OneToMany(mappedBy = "ordersBook", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Bid> bids = new ArrayList<>();
}