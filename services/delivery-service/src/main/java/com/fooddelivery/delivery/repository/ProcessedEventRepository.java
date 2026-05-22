package com.fooddelivery.delivery.repository;

import com.fooddelivery.delivery.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
