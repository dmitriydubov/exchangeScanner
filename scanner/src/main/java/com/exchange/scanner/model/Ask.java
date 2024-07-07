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
public class Ask implements Comparable<Ask> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "orders_book_id", nullable = false)
    private OrdersBook ordersBook;

    @Column(precision = 38, scale = 4)
    private BigDecimal price;

    @Column(precision = 38, scale = 4)
    private BigDecimal volume;

    @Override
    public int compareTo(Ask ask) {
        return price.compareTo(ask.getPrice());
    }
}
