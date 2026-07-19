package com.smartdesk.repository;

import com.smartdesk.model.entity.UserGlobal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserGlobalRepository extends JpaRepository<UserGlobal, UUID> {
    Optional<UserGlobal> findByEmail(String email);
}
