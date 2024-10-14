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
public class Chain implements Comparable<Chain> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 38, scale = 8)
    private BigDecimal commission;

    @Column(name = "min_confirm", nullable = false)
    private Integer minConfirm;

    @ManyToMany(mappedBy = "chains")
    private Set<Coin> coins = new HashSet<>();

    @Override
    public int compareTo(Chain o) {
        return this.commission.compareTo(o.commission);
    }
}
