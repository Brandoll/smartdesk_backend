package com.smartdesk.repository;

import com.smartdesk.model.entity.UserArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserAreaRepository extends JpaRepository<UserArea, UUID> {
    List<UserArea> findByUserId(UUID userId);
    List<UserArea> findByAreaId(UUID areaId);
    void deleteByUserId(UUID userId);
    void deleteByUserIdAndAreaId(UUID userId, UUID areaId);
    boolean existsByUserIdAndAreaId(UUID userId, UUID areaId);
}
