package com.aptech.aptech4.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	public static final String CLAIM_TOKEN_USE = "token_use";
	public static final String CLAIM_EMAIL = "email";
	public static final String CLAIM_ROLES = "roles";
	public static final String TOKEN_USE_ACCESS = "access";
	public static final String TOKEN_USE_REFRESH = "refresh";

	private final JwtProperties properties;
	private final SecretKey accessKey;
	private final SecretKey refreshKey;

	public JwtService(JwtProperties properties) {
		this.properties = properties;
		this.accessKey = Keys.hmacShaKeyFor(properties.getAccessSecret().getBytes(StandardCharsets.UTF_8));
		this.refreshKey = Keys.hmacShaKeyFor(properties.getRefreshSecret().getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(UUID userId, String email, Collection<String> roleCodes) {
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(properties.getAccessTtlSeconds());
		return Jwts.builder()
			.id(UUID.randomUUID().toString())
			.issuer(properties.getIssuer())
			.subject(userId.toString())
			.issuedAt(Date.from(now))
			.expiration(Date.from(exp))
			.claim(CLAIM_TOKEN_USE, TOKEN_USE_ACCESS)
			.claim(CLAIM_EMAIL, email)
			.claim(CLAIM_ROLES, List.copyOf(roleCodes))
			.signWith(accessKey)
			.compact();
	}

	/**
	 * Issues a refresh JWT. Persist only the SHA-256 hash (P04-T03). Not accepted by the API filter.
	 */
	public String createRefreshToken(UUID userId) {
		return issueRefreshToken(userId).token();
	}

	public IssuedRefreshToken issueRefreshToken(UUID userId) {
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(properties.getRefreshTtlSeconds());
		String token = Jwts.builder()
			.id(UUID.randomUUID().toString())
			.issuer(properties.getIssuer())
			.subject(userId.toString())
			.issuedAt(Date.from(now))
			.expiration(Date.from(exp))
			.claim(CLAIM_TOKEN_USE, TOKEN_USE_REFRESH)
			.signWith(refreshKey)
			.compact();
		return new IssuedRefreshToken(token, exp);
	}

	public AccessTokenPrincipal parseAccessToken(String token) {
		Claims claims = parseClaims(token, accessKey);
		String tokenUse = claims.get(CLAIM_TOKEN_USE, String.class);
		if (!TOKEN_USE_ACCESS.equals(tokenUse)) {
			throw new JwtException("Access token required");
		}
		UUID userId = UUID.fromString(claims.getSubject());
		String email = claims.get(CLAIM_EMAIL, String.class);
		@SuppressWarnings("unchecked")
		List<String> roles = claims.get(CLAIM_ROLES, List.class);
		if (roles == null) {
			roles = List.of();
		}
		return new AccessTokenPrincipal(userId, email, List.copyOf(roles));
	}

	public Claims parseRefreshTokenClaims(String token) {
		Claims claims = parseClaims(token, refreshKey);
		if (!TOKEN_USE_REFRESH.equals(claims.get(CLAIM_TOKEN_USE, String.class))) {
			throw new JwtException("Refresh token required");
		}
		return claims;
	}

	private Claims parseClaims(String token, SecretKey key) {
		return Jwts.parser()
			.verifyWith(key)
			.requireIssuer(properties.getIssuer())
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	public boolean isExpired(JwtException ex) {
		return ex instanceof ExpiredJwtException;
	}

	/**
	 * {@link #getName()} is the user id string so STOMP {@code convertAndSendToUser}
	 * and HTTP security share the same principal name.
	 */
	public record AccessTokenPrincipal(UUID userId, String email, List<String> roles) implements Principal {
		@Override
		public String getName() {
			return userId.toString();
		}
	}

	public record IssuedRefreshToken(String token, Instant expiresAt) {
	}
}
