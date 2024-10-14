package com.exchange.scanner.repositories;

import com.exchange.scanner.model.ArbitrageEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArbitrageEventRepository extends JpaRepository<ArbitrageEvent, Long> {

    @EntityGraph(attributePaths = {"eventData"})
    List<ArbitrageEvent> findAll();

    @EntityGraph(attributePaths = {"eventData"})
    Optional<ArbitrageEvent> findByCoin(String coin);
}
