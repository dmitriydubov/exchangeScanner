package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Getter
@Setter
@Table(name = "coins")
public class Coin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_block_by_superuser")
    private Boolean isBlockBySuperuser = false;

    @Override
    @Transient
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coin coin = (Coin) o;
        return Objects.equals(symbol, coin.symbol);
    }

    @Override
    @Transient
    public int hashCode() {
        return Objects.hashCode(symbol);
    }
}
