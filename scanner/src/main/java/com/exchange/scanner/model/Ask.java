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
public class Ask implements Comparable<Ask> {

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
    public int compareTo(Ask ask) {
        return price.compareTo(ask.getPrice());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ask ask = (Ask) o;
        return Objects.equals(price, ask.price) && Objects.equals(volume, ask.volume);
    }

    @Override
    public int hashCode() {
        return Objects.hash(price, volume);
    }
}
