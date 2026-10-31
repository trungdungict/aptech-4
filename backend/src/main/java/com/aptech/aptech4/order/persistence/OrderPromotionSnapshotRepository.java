package com.aptech.aptech4.order.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aptech.aptech4.order.domain.OrderPromotionSnapshot;

public interface OrderPromotionSnapshotRepository extends JpaRepository<OrderPromotionSnapshot, UUID> {

	List<OrderPromotionSnapshot> findByOrderIdOrderByAppliedAtAscIdAsc(UUID orderId);
}
