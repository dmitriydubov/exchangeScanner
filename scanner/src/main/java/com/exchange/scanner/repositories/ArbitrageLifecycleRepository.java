package com.exchange.scanner.repositories;

import com.exchange.scanner.model.ArbitrageLifecycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArbitrageLifecycleRepository extends JpaRepository<ArbitrageLifecycle, Long> {

    Optional<ArbitrageLifecycle> findBySlug(String slug);
}
