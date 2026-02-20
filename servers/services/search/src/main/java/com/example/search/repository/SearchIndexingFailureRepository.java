package com.example.search.repository;

import com.example.search.entity.SearchIndexingFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SearchIndexingFailureRepository extends JpaRepository<SearchIndexingFailure, Long> {

    Optional<SearchIndexingFailure> findByEventIdAndEventType(String eventId, String eventType);
}
