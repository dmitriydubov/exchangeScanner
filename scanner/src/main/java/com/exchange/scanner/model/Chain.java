package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
public class Chain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 38, scale = 4)
    private BigDecimal commission;

    @ManyToMany(mappedBy = "chains", cascade = CascadeType.ALL)
    private Set<Coin> coins = new HashSet<>();
}
