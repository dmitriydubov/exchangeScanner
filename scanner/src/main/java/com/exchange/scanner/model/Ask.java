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

    @Version
    private Long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(precision = 50, scale = 14, nullable = false)
    private BigDecimal price;

    @Column(precision = 50, scale = 14, nullable = false)
    private BigDecimal volume;

    @ManyToOne
    @JoinColumn(name = "orders_book_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private OrdersBook ordersBook;

    @Override
    public int compareTo(Ask ask) {
        return this.price.compareTo(ask.price);
    }
}
