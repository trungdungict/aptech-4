package com.aptech.aptech4.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

class JwtServiceTest {

	private JwtProperties properties;
	private JwtService jwtService;

	@BeforeEach
	void setUp() {
		properties = new JwtProperties();
		properties.setAccessSecret("access-secret-value-32chars-minimum!!");
		properties.setRefreshSecret("refresh-secret-value-32chars-minimum!");
		properties.setIssuer("aptech4-api");
		properties.setAccessTtlSeconds(900);
		properties.setRefreshTtlSeconds(3600);
		properties.rejectWeakOrIdenticalSecrets();
		jwtService = new JwtService(properties);
	}

	@Test
	void createAndParseAccessToken() {
		UUID userId = UUID.randomUUID();
		String token = jwtService.createAccessToken(userId, "cust@example.com", List.of("CUSTOMER"));

		JwtService.AccessTokenPrincipal principal = jwtService.parseAccessToken(token);

		assertEquals(userId, principal.userId());
		assertEquals("cust@example.com", principal.email());
		assertEquals(List.of("CUSTOMER"), principal.roles());
	}

	@Test
	void refreshTokenRejectedAsAccessToken() {
		String refresh = jwtService.createRefreshToken(UUID.randomUUID());
		assertThrows(JwtException.class, () -> jwtService.parseAccessToken(refresh));
	}

	@Test
	void wrongIssuerRejected() {
		UUID userId = UUID.randomUUID();
		String token = Jwts.builder()
			.issuer("evil-issuer")
			.subject(userId.toString())
			.issuedAt(new Date())
			.expiration(Date.from(Instant.now().plusSeconds(60)))
			.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("CUSTOMER"))
			.signWith(Keys.hmacShaKeyFor(properties.getAccessSecret().getBytes(StandardCharsets.UTF_8)))
			.compact();

		assertThrows(JwtException.class, () -> jwtService.parseAccessToken(token));
	}

	@Test
	void wrongSignatureRejected() {
		UUID userId = UUID.randomUUID();
		String token = Jwts.builder()
			.issuer(properties.getIssuer())
			.subject(userId.toString())
			.issuedAt(new Date())
			.expiration(Date.from(Instant.now().plusSeconds(60)))
			.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("CUSTOMER"))
			.signWith(Keys.hmacShaKeyFor("different-secret-32chars-minimum!!!!".getBytes(StandardCharsets.UTF_8)))
			.compact();

		assertThrows(JwtException.class, () -> jwtService.parseAccessToken(token));
	}

	@Test
	void expiredAccessTokenRejected() {
		properties.setAccessTtlSeconds(1);
		JwtService shortLived = new JwtService(properties);
		String token = shortLived.createAccessToken(UUID.randomUUID(), "x@example.com", List.of("CUSTOMER"));

		// Force expiry by crafting already-expired token
		String expired = Jwts.builder()
			.issuer(properties.getIssuer())
			.subject(UUID.randomUUID().toString())
			.issuedAt(Date.from(Instant.now().minusSeconds(120)))
			.expiration(Date.from(Instant.now().minusSeconds(60)))
			.claim(JwtService.CLAIM_TOKEN_USE, JwtService.TOKEN_USE_ACCESS)
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("CUSTOMER"))
			.signWith(Keys.hmacShaKeyFor(properties.getAccessSecret().getBytes(StandardCharsets.UTF_8)))
			.compact();

		JwtException ex = assertThrows(JwtException.class, () -> jwtService.parseAccessToken(expired));
		assertTrue(ex instanceof ExpiredJwtException || jwtService.isExpired(ex));
	}

	@Test
	void wrongTokenUseRejectedEvenWithValidAccessSignature() {
		String token = Jwts.builder()
			.issuer(properties.getIssuer())
			.subject(UUID.randomUUID().toString())
			.issuedAt(new Date())
			.expiration(Date.from(Instant.now().plusSeconds(60)))
			.claim(JwtService.CLAIM_TOKEN_USE, "session")
			.claim(JwtService.CLAIM_EMAIL, "x@example.com")
			.claim(JwtService.CLAIM_ROLES, List.of("ADMIN"))
			.signWith(Keys.hmacShaKeyFor(properties.getAccessSecret().getBytes(StandardCharsets.UTF_8)))
			.compact();

		assertThrows(JwtException.class, () -> jwtService.parseAccessToken(token));
	}

	@Test
	void accessTokenRejectedAsRefreshClaims() {
		String access = jwtService.createAccessToken(UUID.randomUUID(), "x@example.com", List.of("CUSTOMER"));
		assertThrows(JwtException.class, () -> jwtService.parseRefreshTokenClaims(access));
	}
}
