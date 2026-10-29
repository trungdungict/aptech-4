package com.aptech.aptech4.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.identity.api.AuthResponse;
import com.aptech.aptech4.identity.api.LoginRequest;
import com.aptech.aptech4.identity.api.LogoutRequest;
import com.aptech.aptech4.identity.api.RefreshRequest;
import com.aptech.aptech4.identity.api.RegisterRequest;
import com.aptech.aptech4.identity.domain.RefreshToken;
import com.aptech.aptech4.identity.domain.Role;
import com.aptech.aptech4.identity.domain.RoleCode;
import com.aptech.aptech4.identity.domain.UserAccount;
import com.aptech.aptech4.identity.persistence.RefreshTokenRepository;
import com.aptech.aptech4.identity.persistence.RoleRepository;
import com.aptech.aptech4.identity.persistence.UserAccountRepository;
import com.aptech.aptech4.identity.security.AccountUserDetails;
import com.aptech.aptech4.identity.security.EmailNormalizer;
import com.aptech.aptech4.identity.security.JwtProperties;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.RefreshTokenHasher;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

@Service
public class AuthService {

	private static final String INVALID_REFRESH = "Invalid refresh token";

	private final UserAccountRepository userAccountRepository;
	private final RoleRepository roleRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final JwtProperties jwtProperties;
	private final TransactionTemplate requiresNewTx;

	public AuthService(
			UserAccountRepository userAccountRepository,
			RoleRepository roleRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager,
			JwtService jwtService,
			JwtProperties jwtProperties,
			PlatformTransactionManager transactionManager
	) {
		this.userAccountRepository = userAccountRepository;
		this.roleRepository = roleRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
		this.jwtProperties = jwtProperties;
		this.requiresNewTx = new TransactionTemplate(transactionManager);
		this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = EmailNormalizer.normalize(request.email());
		Role customerRole = roleRepository.findByCode(RoleCode.CUSTOMER)
			.orElseThrow(() -> new ApiException(
					HttpStatus.INTERNAL_SERVER_ERROR,
					"Configuration Error",
					"CUSTOMER role is not seeded"
			));

		UserAccount account = UserAccount.createNew(
				email,
				passwordEncoder.encode(request.password()),
				request.fullName().trim()
		);
		account.getRoles().add(customerRole);

		try {
			account = userAccountRepository.saveAndFlush(account);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "Conflict", "Email already registered");
		}

		return issueSession(account);
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		String email = EmailNormalizer.normalize(request.email());
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(email, request.password())
		);
		AccountUserDetails principal = (AccountUserDetails) authentication.getPrincipal();
		UserAccount account = userAccountRepository.findByEmailWithRoles(principal.getUsername())
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password"));
		return issueSession(account);
	}

	/**
	 * Rotates the presented refresh token. Concurrent callers on the same token:
	 * pessimistic row lock ensures only one rotate succeeds; the other sees a revoked row.
	 */
	@Transactional
	public AuthResponse refresh(RefreshRequest request) {
		Claims claims = parseRefreshClaimsOrUnauthorized(request.refreshToken());
		UUID subject = parseSubject(claims);
		String hash = RefreshTokenHasher.sha256Hex(request.refreshToken());

		RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash)
			.orElseThrow(this::invalidRefresh);

		Instant now = Instant.now();
		if (stored.getRevokedAt() != null) {
			// Reuse of a rotated/revoked token → revoke all active sessions (separate TX so it survives this failure).
			UUID userId = stored.getUser().getId();
			requiresNewTx.executeWithoutResult(status ->
					refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now()));
			throw invalidRefresh();
		}
		if (!stored.getExpiresAt().isAfter(now) || claims.getExpiration().toInstant().isBefore(now)) {
			stored.setRevokedAt(now);
			throw invalidRefresh();
		}

		UserAccount user = stored.getUser();
		if (!user.isEnabled() || !user.getId().equals(subject)) {
			stored.setRevokedAt(now);
			throw invalidRefresh();
		}

		JwtService.IssuedRefreshToken issued = jwtService.issueRefreshToken(user.getId());
		RefreshToken replacement = RefreshToken.createNew(
				user,
				RefreshTokenHasher.sha256Hex(issued.token()),
				issued.expiresAt()
		);
		replacement = refreshTokenRepository.saveAndFlush(replacement);

		stored.setRevokedAt(now);
		stored.setReplacedBy(replacement);
		refreshTokenRepository.save(stored);

		return toAuthResponse(user, issued.token());
	}

	/**
	 * Revokes a single refresh token (this device/session). Idempotent: unknown or already-revoked
	 * tokens still return success. Does not invalidate outstanding access JWTs (stateless; no denylist).
	 */
	@Transactional
	public void logout(LogoutRequest request) {
		String raw = request.refreshToken();
		String hash = RefreshTokenHasher.sha256Hex(raw);
		refreshTokenRepository.findByTokenHashForUpdate(hash).ifPresent(token -> {
			if (token.getRevokedAt() == null) {
				token.setRevokedAt(Instant.now());
				refreshTokenRepository.save(token);
			}
		});
		// Intentionally ignore JWT parse failures — logout must not leak token validity.
	}

	private AuthResponse issueSession(UserAccount account) {
		JwtService.IssuedRefreshToken issued = jwtService.issueRefreshToken(account.getId());
		RefreshToken persisted = RefreshToken.createNew(
				account,
				RefreshTokenHasher.sha256Hex(issued.token()),
				issued.expiresAt()
		);
		refreshTokenRepository.save(persisted);
		return toAuthResponse(account, issued.token());
	}

	private AuthResponse toAuthResponse(UserAccount account, String refreshToken) {
		List<String> roles = account.getRoles().stream()
			.map(role -> role.getCode().name())
			.sorted()
			.toList();
		String accessToken = jwtService.createAccessToken(account.getId(), account.getEmail(), roles);
		return new AuthResponse(
				accessToken,
				refreshToken,
				"Bearer",
				jwtProperties.getAccessTtlSeconds(),
				new AuthResponse.UserResponse(account.getId(), account.getEmail(), account.getFullName(), roles)
		);
	}

	private Claims parseRefreshClaimsOrUnauthorized(String rawToken) {
		try {
			return jwtService.parseRefreshTokenClaims(rawToken);
		}
		catch (JwtException | IllegalArgumentException ex) {
			throw invalidRefresh();
		}
	}

	private UUID parseSubject(Claims claims) {
		try {
			return UUID.fromString(claims.getSubject());
		}
		catch (RuntimeException ex) {
			throw invalidRefresh();
		}
	}

	private ApiException invalidRefresh() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", INVALID_REFRESH);
	}
}
