package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.Objects;
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

    @Column(name = "is_block_by_superuser")
    private Boolean isBlockBySuperuser = false;

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(
            name = "exchanges_coins",
            joinColumns = @JoinColumn(name = "coin_id"),
            inverseJoinColumns = @JoinColumn(name = "exchange_id")
    )
    @BatchSize(size = 1000)
    private Set<Coin> coins = new HashSet<>();

    @Override
    @Transient
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Exchange exchange = (Exchange) o;
        return Objects.equals(name, exchange.name);
    }

    @Override
    @Transient
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
