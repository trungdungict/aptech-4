package com.aptech.aptech4.identity.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aptech.aptech4.identity.domain.Address;

public interface AddressRepository extends JpaRepository<Address, UUID> {

	@Query("""
			select a from Address a
			join fetch a.user
			where a.id = :id and a.user.id = :userId
			""")
	Optional<Address> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

	List<Address> findByUser_IdOrderByDefaultAddressDescCreatedAtDesc(UUID userId);
}
