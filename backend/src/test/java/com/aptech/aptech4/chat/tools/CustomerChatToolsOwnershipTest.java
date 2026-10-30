package com.aptech.aptech4.chat.tools;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.aptech.aptech4.catalog.application.ProductService;
import com.aptech.aptech4.chat.config.AiProperties;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.SecurityRoles;
import com.aptech.aptech4.order.application.OrderService;

/**
 * Customer tools never accept a customer id from the prompt; ownership is JWT-only.
 * Cross-customer order access surfaces as not-found from {@link OrderService#getMine}.
 */
@ExtendWith(MockitoExtension.class)
class CustomerChatToolsOwnershipTest {

	@Mock
	ProductService productService;

	@Mock
	OrderService orderService;

	CustomerChatTools tools;

	UUID customerA;

	@BeforeEach
	void setUp() {
		AiProperties props = new AiProperties();
		props.setMaxToolResults(20);
		tools = new CustomerChatTools(productService, orderService, props);
		customerA = UUID.randomUUID();
		var principal = new JwtService.AccessTokenPrincipal(customerA, "a@example.com", List.of("CUSTOMER"));
		var auth = new UsernamePasswordAuthenticationToken(
				principal, null, SecurityRoles.authorities(principal.roles())
		);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void getMyOrderDetailsUsesJwtScopedGetMineAndSurfacesCrossCustomerAsNotFound() {
		UUID foreignOrderId = UUID.randomUUID();
		when(orderService.getMine(foreignOrderId))
				.thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Not Found", "Order not found"));

		assertThatThrownBy(() -> tools.getMyOrderDetails(foreignOrderId.toString()))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.NOT_FOUND);

		verify(orderService).getMine(foreignOrderId);
		verifyNoMoreInteractions(orderService);
	}

	@Test
	void getMyOrdersRequiresAuthenticatedCurrentUser() {
		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> tools.getMyOrders(null, 5))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getStatus())
				.isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
