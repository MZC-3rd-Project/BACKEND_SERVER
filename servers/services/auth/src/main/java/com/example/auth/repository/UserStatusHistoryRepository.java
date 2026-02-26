package com.example.auth.repository;

import com.example.auth.entity.UserStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, Long> {

    List<UserStatusHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
