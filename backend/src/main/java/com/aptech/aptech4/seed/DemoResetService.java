package com.aptech.aptech4.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aptech.aptech4.identity.security.EmailNormalizer;

/**
 * Deletes only rows keyed by seed natural keys (emails / slugs / SKUs from {@link DemoSeedCatalog}
 * + configured demo emails). Never truncates tables or drops volumes.
 */
@Service
public class DemoResetService {

	private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);
	private static final String CONFIRM_TOKEN = "yes";

	private final DemoSeedProperties properties;
	private final Environment environment;
	private final JdbcTemplate jdbcTemplate;
	private final DataSource dataSource;

	public DemoResetService(
			DemoSeedProperties properties,
			Environment environment,
			JdbcTemplate jdbcTemplate,
			DataSource dataSource
	) {
		this.properties = properties;
		this.environment = environment;
		this.jdbcTemplate = jdbcTemplate;
		this.dataSource = dataSource;
	}

	public DemoResetPlan plan() {
		DemoSeedGuard.assertAllowed(environment, properties.isAllowOnProd());
		String jdbcUrl = requireJdbcUrl();
		List<String> emails = demoEmails();
		List<String> slugs = DemoSeedMarkers.categorySlugs();
		List<String> skus = DemoSeedMarkers.productSkus();

		List<UUID> userIds = queryUuids(
				"select id from users where email in (" + placeholders(emails.size()) + ")",
				emails.toArray()
		);
		List<UUID> productIds = queryUuids(
				"select id from products where sku in (" + placeholders(skus.size()) + ")",
				skus.toArray()
		);
		List<UUID> categoryIds = queryUuids(
				"select id from categories where slug in (" + placeholders(slugs.size()) + ")",
				slugs.toArray()
		);

		int orders = countDependentOrders(userIds, productIds);
		int carts = userIds.isEmpty() ? 0 : count(
				"select count(*) from carts where user_id in (" + placeholders(userIds.size()) + ")",
				userIds.toArray()
		);
		int reservations = countDependentReservations(userIds, productIds);

		return new DemoResetPlan(
				jdbcUrl,
				List.copyOf(emails),
				List.copyOf(slugs),
				List.copyOf(skus),
				userIds.size(),
				categoryIds.size(),
				productIds.size(),
				orders,
				carts,
				reservations
		);
	}

	/**
	 * @param confirm must equal {@code yes} (case-sensitive)
	 */
	@Transactional
	public DemoResetResult execute(String confirm) {
		DemoSeedGuard.assertAllowed(environment, properties.isAllowOnProd());
		if (!CONFIRM_TOKEN.equals(confirm)) {
			throw new IllegalStateException(
					"Demo reset refused: set aptech.seed.reset-confirm=yes (got '" + confirm + "')"
			);
		}
		requireJdbcUrl();

		List<String> emails = demoEmails();
		List<String> slugs = DemoSeedMarkers.categorySlugs();
		List<String> skus = DemoSeedMarkers.productSkus();

		List<UUID> userIds = queryUuids(
				"select id from users where email in (" + placeholders(emails.size()) + ")",
				emails.toArray()
		);
		List<UUID> productIds = queryUuids(
				"select id from products where sku in (" + placeholders(skus.size()) + ")",
				skus.toArray()
		);
		List<UUID> categoryIds = queryUuids(
				"select id from categories where slug in (" + placeholders(slugs.size()) + ")",
				slugs.toArray()
		);

		List<UUID> orderIds = findOrderIds(userIds, productIds);
		List<UUID> reservationIds = findReservationIds(userIds, productIds);

		int reservationsDeleted = 0;
		if (!reservationIds.isEmpty()) {
			Object[] resArgs = reservationIds.toArray();
			jdbcTemplate.update(
					"delete from stock_reservation_items where reservation_id in ("
							+ placeholders(reservationIds.size()) + ")",
					resArgs
			);
			reservationsDeleted = jdbcTemplate.update(
					"delete from stock_reservations where id in (" + placeholders(reservationIds.size()) + ")",
					resArgs
			);
		}
		if (!productIds.isEmpty()) {
			jdbcTemplate.update(
					"delete from stock_reservation_items where product_id in ("
							+ placeholders(productIds.size()) + ")",
					productIds.toArray()
			);
		}

		int cartsDeleted = 0;
		if (!userIds.isEmpty()) {
			Object[] userArgs = userIds.toArray();
			jdbcTemplate.update(
					"delete from cart_items where cart_id in (select id from carts where user_id in ("
							+ placeholders(userIds.size()) + "))",
					userArgs
			);
			cartsDeleted = jdbcTemplate.update(
					"delete from carts where user_id in (" + placeholders(userIds.size()) + ")",
					userArgs
			);
		}
		if (!productIds.isEmpty()) {
			jdbcTemplate.update(
					"delete from cart_items where product_id in (" + placeholders(productIds.size()) + ")",
					productIds.toArray()
			);
		}

		int ordersDeleted = 0;
		if (!orderIds.isEmpty()) {
			Object[] orderArgs = orderIds.toArray();
			// payments / checkout_idempotency cascade from orders; delete items then orders
			jdbcTemplate.update(
					"delete from order_items where order_id in (" + placeholders(orderIds.size()) + ")",
					orderArgs
			);
			ordersDeleted = jdbcTemplate.update(
					"delete from orders where id in (" + placeholders(orderIds.size()) + ")",
					orderArgs
			);
		}

		if (!userIds.isEmpty()) {
			Object[] userArgs = userIds.toArray();
			jdbcTemplate.update(
					"delete from notifications where recipient_user_id in (" + placeholders(userIds.size()) + ")",
					userArgs
			);
			jdbcTemplate.update(
					"delete from refresh_tokens where user_id in (" + placeholders(userIds.size()) + ")",
					userArgs
			);
			jdbcTemplate.update(
					"delete from checkout_locks where user_id in (" + placeholders(userIds.size()) + ")",
					userArgs
			);
			jdbcTemplate.update(
					"delete from checkout_idempotency where user_id in (" + placeholders(userIds.size()) + ")",
					userArgs
			);
			jdbcTemplate.update(
					"delete from addresses where user_id in (" + placeholders(userIds.size()) + ")",
					userArgs
			);
			jdbcTemplate.update(
					"delete from user_roles where user_id in (" + placeholders(userIds.size()) + ")",
					userArgs
			);
		}

		int productsDeleted = 0;
		if (!productIds.isEmpty()) {
			Object[] productArgs = productIds.toArray();
			jdbcTemplate.update(
					"delete from inventory where product_id in (" + placeholders(productIds.size()) + ")",
					productArgs
			);
			productsDeleted = jdbcTemplate.update(
					"delete from products where id in (" + placeholders(productIds.size()) + ")",
					productArgs
			);
		}

		int categoriesDeleted = 0;
		if (!categoryIds.isEmpty()) {
			categoriesDeleted = jdbcTemplate.update(
					"delete from categories where id in (" + placeholders(categoryIds.size()) + ")",
					categoryIds.toArray()
			);
		}

		int usersDeleted = 0;
		if (!userIds.isEmpty()) {
			usersDeleted = jdbcTemplate.update(
					"delete from users where id in (" + placeholders(userIds.size()) + ")",
					userIds.toArray()
			);
		}

		DemoResetResult result = new DemoResetResult(
				usersDeleted,
				categoriesDeleted,
				productsDeleted,
				ordersDeleted,
				cartsDeleted,
				reservationsDeleted,
				false
		);
		log.info("Demo reset executed: {}", result);
		return result;
	}

	private List<String> demoEmails() {
		List<String> emails = new ArrayList<>();
		if (StringUtils.hasText(properties.getAdminEmail())) {
			emails.add(EmailNormalizer.normalize(properties.getAdminEmail()));
		}
		if (StringUtils.hasText(properties.getCustomerEmail())) {
			emails.add(EmailNormalizer.normalize(properties.getCustomerEmail()));
		}
		if (emails.isEmpty()) {
			throw new IllegalStateException("Demo reset refused: no demo emails configured");
		}
		return emails;
	}

	private String requireJdbcUrl() {
		try (var connection = dataSource.getConnection()) {
			String url = connection.getMetaData().getURL();
			if (!StringUtils.hasText(url)) {
				throw new IllegalStateException("Demo reset refused: JDBC URL is blank");
			}
			return url;
		}
		catch (IllegalStateException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Demo reset refused: cannot resolve JDBC URL", ex);
		}
	}

	private List<UUID> findOrderIds(List<UUID> userIds, List<UUID> productIds) {
		List<UUID> orderIds = new ArrayList<>();
		if (!userIds.isEmpty()) {
			orderIds.addAll(queryUuids(
					"select id from orders where user_id in (" + placeholders(userIds.size()) + ")",
					userIds.toArray()
			));
		}
		if (!productIds.isEmpty()) {
			orderIds.addAll(queryUuids(
					"select distinct order_id from order_items where product_id in ("
							+ placeholders(productIds.size()) + ")",
					productIds.toArray()
			));
		}
		return orderIds.stream().distinct().toList();
	}

	private List<UUID> findReservationIds(List<UUID> userIds, List<UUID> productIds) {
		List<UUID> ids = new ArrayList<>();
		if (!userIds.isEmpty()) {
			ids.addAll(queryUuids(
					"select id from stock_reservations where user_id in (" + placeholders(userIds.size()) + ")",
					userIds.toArray()
			));
		}
		if (!productIds.isEmpty()) {
			ids.addAll(queryUuids(
					"select distinct reservation_id from stock_reservation_items where product_id in ("
							+ placeholders(productIds.size()) + ")",
					productIds.toArray()
			));
		}
		return ids.stream().distinct().toList();
	}

	private int countDependentOrders(List<UUID> userIds, List<UUID> productIds) {
		return findOrderIds(userIds, productIds).size();
	}

	private int countDependentReservations(List<UUID> userIds, List<UUID> productIds) {
		return findReservationIds(userIds, productIds).size();
	}

	private List<UUID> queryUuids(String sql, Object... args) {
		if (args.length == 0) {
			return List.of();
		}
		return jdbcTemplate.query(sql, (rs, rowNum) -> (UUID) rs.getObject(1), args);
	}

	private int count(String sql, Object... args) {
		Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
		return value == null ? 0 : value;
	}

	private static String placeholders(int n) {
		if (n <= 0) {
			throw new IllegalArgumentException("placeholders require n > 0");
		}
		return String.join(",", java.util.Collections.nCopies(n, "?"));
	}
}
