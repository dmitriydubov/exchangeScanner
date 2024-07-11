package com.exchange.scanner.repositories;

import com.exchange.scanner.model.ArbitrageOpportunityLifecycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArbitrageOpportunitiesLifecycleRepository extends JpaRepository<ArbitrageOpportunityLifecycle, Long> {
}
