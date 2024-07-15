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

    @Column(precision = 38, scale = 8, nullable = false)
    private BigDecimal price;

    @Column(precision = 38, scale = 8, nullable = false)
    private BigDecimal volume;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "orders_book_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private OrdersBook ordersBook;

    @Override
    public int compareTo(Bid bid) {
        return bid.price.compareTo(this.price);
    }
}
