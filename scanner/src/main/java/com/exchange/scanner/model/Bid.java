package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Setter
public class Bid implements Comparable<Bid> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(precision = 38, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(precision = 38, scale = 6, nullable = false)
    private BigDecimal volume;

    @ManyToOne
    private OrdersBook ordersBook;

    @Override
    public int compareTo(Bid bid) {
        if (price.compareTo(bid.price) > 0) return -1;
        if (price.compareTo(bid.price) < 0) return 1;
        return price.compareTo(bid.price);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bid bid = (Bid) o;
        return Objects.equals(price, bid.price) && Objects.equals(volume, bid.volume);
    }

    @Override
    public int hashCode() {
        return Objects.hash(price, volume);
    }
}
