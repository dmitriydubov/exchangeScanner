package com.exchange.scanner.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
public class Bid implements Comparable<Bid> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "orders_book_id", nullable = false)
    private OrdersBook ordersBook;

    @Column(precision = 46, scale = 5)
    private BigDecimal price;

    @Column(precision = 46, scale = 5)
    private BigDecimal volume;

    @Override
    public int compareTo(Bid bid) {
        return price.compareTo(bid.price);
    }
}
