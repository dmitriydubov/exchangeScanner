package com.exchange.scanner.repositories;

import com.exchange.scanner.model.Coin;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoinRepository extends JpaRepository<Coin, Long> {

    @EntityGraph(attributePaths = {"chains"})
    Optional<Coin> findBySlug(String slug);

    List<Coin> findByName(String coinName);

    void deleteAllInBatch(Iterable<Coin> entities);
}
