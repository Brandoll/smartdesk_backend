package com.smartdesk.repository;

import com.smartdesk.model.entity.Area;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AreaRepository extends JpaRepository<Area, UUID> {
    Optional<Area> findByName(String name);
}
