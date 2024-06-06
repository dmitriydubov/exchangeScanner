package com.exchange.scanner.repositories;

import com.exchange.scanner.model.ArbitrageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArbitrageEventRepository extends JpaRepository<ArbitrageEvent, Long> {
}
