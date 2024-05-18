package com.exchange.scanner.repositories;

import com.exchange.scanner.model.Coin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoinRepository extends JpaRepository<Coin, Integer> {
}
