package com.exchange.scanner.repositories;

import com.exchange.scanner.model.Ask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AskRepository extends JpaRepository<Ask, Long> {
}
