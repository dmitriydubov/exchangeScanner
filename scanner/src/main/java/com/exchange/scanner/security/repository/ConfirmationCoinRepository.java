package com.exchange.scanner.security.repository;

import com.exchange.scanner.security.model.ConfirmationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfirmationCoinRepository extends JpaRepository<ConfirmationCode, Long> {

    Optional<ConfirmationCode> findByEmail(String email);

    void deleteByEmail(String email);
}
