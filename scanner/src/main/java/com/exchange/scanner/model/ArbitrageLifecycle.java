package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ArbitrageLifecycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private Long timestamp;

    @Column(name = "last_update", nullable = false)
    private Long lastUpdate;
}
