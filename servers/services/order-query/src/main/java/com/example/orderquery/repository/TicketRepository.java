package com.example.orderquery.repository;

import com.example.orderquery.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByOrderItem_Id(Long orderItemId);
}
