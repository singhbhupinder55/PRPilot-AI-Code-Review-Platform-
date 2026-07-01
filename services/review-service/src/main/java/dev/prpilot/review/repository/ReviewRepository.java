package dev.prpilot.review.repository;

import dev.prpilot.review.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Optional<Review> findByDeliveryId(String deliveryId);
}