package com.smartdesk.repository;

import com.smartdesk.model.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {
    Page<Ticket> findByClientId(UUID clientId, Pageable pageable);
    Page<Ticket> findByAssignedToId(UUID assignedToId, Pageable pageable);
    Page<Ticket> findByAreaId(UUID areaId, Pageable pageable);
    Page<Ticket> findByStatus(Ticket.Status status, Pageable pageable);
}
