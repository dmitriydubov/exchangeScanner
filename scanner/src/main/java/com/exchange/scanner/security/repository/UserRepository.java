package com.exchange.scanner.security.repository;

import com.exchange.scanner.security.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Boolean existsByUsername(String username);

    Boolean existsByTelegram(String telegram);

    Boolean existsByEmail(String email);
}
