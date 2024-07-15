package com.exchange.scanner.repositories;

import com.exchange.scanner.model.OrdersBook;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrdersBookRepository extends JpaRepository<OrdersBook, Long> {
    void deleteBySlug(String slug);

    @EntityGraph(attributePaths = {"asks", "bids"})
    Optional<OrdersBook> findBySlug(String slug);
}
