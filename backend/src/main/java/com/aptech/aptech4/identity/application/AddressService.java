package com.aptech.aptech4.identity.application;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.identity.api.AddressCreateRequest;
import com.aptech.aptech4.identity.api.AddressResponse;
import com.aptech.aptech4.identity.domain.Address;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.AddressRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.CurrentUser;

@Service
public class AddressService {

	private final AddressRepository addressRepository;
	private final UserAccountRepository userAccountRepository;

	public AddressService(AddressRepository addressRepository, UserAccountRepository userAccountRepository) {
		this.addressRepository = addressRepository;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional(readOnly = true)
	public List<AddressResponse> listMine() {
		UUID userId = CurrentUser.requireUserId();
		return addressRepository.findByUser_IdOrderByDefaultAddressDescCreatedAtDesc(userId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional
	public AddressResponse create(AddressCreateRequest request) {
		UUID userId = CurrentUser.requireUserId();
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "User not found"));

		boolean makeDefault = Boolean.TRUE.equals(request.defaultAddress())
				|| addressRepository.findByUser_IdOrderByDefaultAddressDescCreatedAtDesc(userId).isEmpty();

		Address address = Address.createNew(
				user,
				request.recipientName().trim(),
				request.line1().trim(),
				blankToNull(request.line2()),
				request.city().trim(),
				blankToNull(request.stateRegion()),
				blankToNull(request.postalCode()),
				request.countryCode() == null || request.countryCode().isBlank() ? "VN" : request.countryCode().trim().toUpperCase(),
				blankToNull(request.phone()),
				makeDefault
		);
		if (request.label() != null && !request.label().isBlank()) {
			address.setLabel(request.label().trim());
		}
		return toResponse(addressRepository.saveAndFlush(address));
	}

	private AddressResponse toResponse(Address address) {
		return new AddressResponse(
				address.getId(),
				address.getLabel(),
				address.getRecipientName(),
				address.getLine1(),
				address.getLine2(),
				address.getCity(),
				address.getStateRegion(),
				address.getPostalCode(),
				address.getCountryCode(),
				address.getPhone(),
				address.isDefaultAddress()
		);
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
