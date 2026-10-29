package com.aptech.aptech4.catalog.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.catalog.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

	@Query("""
			select p from Product p
			join fetch p.category c
			left join fetch p.inventory
			where p.id = :id and p.active = true and c.active = true
			""")
	Optional<Product> findPublicById(@Param("id") UUID id);

	@Query("""
			select p from Product p
			join fetch p.category c
			where p.id = :id and p.active = true and c.active = true
			""")
	Optional<Product> findPurchasableById(@Param("id") UUID id);

	@Query("""
			select p from Product p
			join fetch p.category
			left join fetch p.inventory
			where p.id = :id
			""")
	Optional<Product> findAdminById(@Param("id") UUID id);

	@Query("""
			select distinct p from Product p
			join fetch p.category
			left join fetch p.inventory
			where p.id in :ids
			""")
	List<Product> findAllWithCatalogByIdIn(@Param("ids") Collection<UUID> ids);

	boolean existsBySkuIgnoreCase(String sku);

	boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID id);

	Optional<Product> findBySkuIgnoreCase(String sku);
}
