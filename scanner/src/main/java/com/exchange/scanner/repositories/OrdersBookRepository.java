package com.exchange.scanner.repositories;

import com.exchange.scanner.model.OrdersBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdersBookRepository extends JpaRepository<OrdersBook, Long> {

    List<OrdersBook> findByCoinName(String coinName);
}
