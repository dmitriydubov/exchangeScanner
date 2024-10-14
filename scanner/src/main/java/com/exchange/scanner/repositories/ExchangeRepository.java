package com.exchange.scanner.repositories;

import com.exchange.scanner.model.Exchange;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ExchangeRepository extends JpaRepository<Exchange, Integer> {

    @EntityGraph(attributePaths = {"name", "isBlockBySuperuser", "coins"})
    Set<Exchange> findAllByNameIn(List<String> names);

    Exchange findByName(String key);

    @Query("SELECT e.name, c.name FROM Exchange e JOIN e.coins c")
    List<Object[]> findExchangeAndCoinData();
}
