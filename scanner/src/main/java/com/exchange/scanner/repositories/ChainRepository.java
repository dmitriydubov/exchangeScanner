package com.exchange.scanner.repositories;

import com.exchange.scanner.model.Chain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChainRepository extends JpaRepository<Chain, Long> {
}
