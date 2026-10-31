package com.aptech.aptech4.promotion.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.promotion.domain.PromotionStrategy;

public interface PromotionStrategyRepository extends JpaRepository<PromotionStrategy, UUID> {

	@Query("""
			select case when count(s) > 0 then true else false end
			from PromotionStrategy s
			where lower(s.name) = lower(:name)
			""")
	boolean existsByNameIgnoreCase(@Param("name") String name);

	@Query("""
			select case when count(s) > 0 then true else false end
			from PromotionStrategy s
			where lower(s.name) = lower(:name) and s.id <> :id
			""")
	boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") UUID id);

	Optional<PromotionStrategy> findByCodeIgnoreCase(String code);
}
