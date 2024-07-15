package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Entity
@Getter
@Setter
@Table(
        name = "orders_book",
        indexes = @Index(name = "idx_orders_book_slug", columnList = "slug")
)
public class OrdersBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String slug;

    @OneToOne(mappedBy = "ordersBook", cascade = CascadeType.ALL)
    @JoinColumn(name = "orders_book_id")
    private Coin coin;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "orders_book_id")
    private Set<Ask> asks = new TreeSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "orders_book_id")
    private Set<Bid> bids = new TreeSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrdersBook that = (OrdersBook) o;
        return Objects.equals(slug, that.slug);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(slug);
    }
}