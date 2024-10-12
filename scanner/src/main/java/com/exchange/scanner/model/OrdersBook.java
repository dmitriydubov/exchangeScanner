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

    @Version
    private Long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @OneToOne(mappedBy = "ordersBook", cascade = CascadeType.ALL)
    @JoinColumn(name = "orders_book_id")
    private Coin coin;

    private String timestamp;

    @OneToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE}, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "orders_book_id")
    private Set<Ask> asks = new TreeSet<>();

    @OneToMany(cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE}, fetch = FetchType.LAZY, orphanRemoval = true)
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