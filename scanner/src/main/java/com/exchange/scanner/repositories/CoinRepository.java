package com.exchange.scanner.repositories;

import com.exchange.scanner.model.Coin;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoinRepository extends JpaRepository<Coin, Long> {
    List<Coin> findByName(String coinName);

    void deleteAllInBatch(@NotNull Iterable<Coin> entities);
}
