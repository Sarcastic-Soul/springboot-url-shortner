package com.anish.url_shortener.auth.repository;

import com.anish.url_shortener.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmail(String email);
}
