package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
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

    @ManyToMany(mappedBy = "coins", cascade = CascadeType.ALL)
    private Set<Exchange> exchanges = new HashSet<>();

    @Transient
    @ManyToMany(mappedBy = "coin", cascade = CascadeType.ALL)
    private Set<OrdersBook> ordersBooks = new HashSet<>();
}