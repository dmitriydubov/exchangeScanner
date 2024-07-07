package com.exchange.scanner.repositories;

import com.exchange.scanner.model.Coin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoinRepository extends JpaRepository<Coin, Long> {

    List<Coin> findByName(String coinName);
}
