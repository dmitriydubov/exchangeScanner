package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.processing.SQL;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


@Entity
@Getter
@Setter
@Table(name = "exchanges", indexes = {
        @Index(name = "idx_exchange_name", columnList = "name"),
        @Index(name = "idx_exchange_isBlockBySuperuser", columnList = "is_block_by_superuser")
})
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

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
            name = "exchanges_coins",
            joinColumns = @JoinColumn(name = "exchange_id"),
            inverseJoinColumns = @JoinColumn(name = "coin_id")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    @BatchSize(size = 1000)
    private Set<Coin> coins = new HashSet<>();
}
