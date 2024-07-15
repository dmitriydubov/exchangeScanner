package com.exchange.scanner.repositories;

import com.exchange.scanner.model.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, Long> {
}
