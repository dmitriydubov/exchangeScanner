package com.exchange.scanner.repositories;

import com.exchange.scanner.model.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ExchangeRepository extends JpaRepository<Exchange, Integer> {
    List<Exchange> findAllByNameIn(Set<String> names);

    Exchange findByName(String key);
}
