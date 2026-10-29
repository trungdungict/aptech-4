package com.aptech.aptech4.seed;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class DemoResetServiceGuardTest {

	private DemoSeedProperties properties;
	private MockEnvironment environment;
	private DataSource dataSource;
	private DemoResetService service;

	@BeforeEach
	void setUp() throws Exception {
		properties = new DemoSeedProperties();
		properties.setAdminEmail("admin@aptech4.local");
		properties.setCustomerEmail("customer@aptech4.local");
		environment = new MockEnvironment();
		dataSource = mock(DataSource.class);
		Connection connection = mock(Connection.class);
		DatabaseMetaData metaData = mock(DatabaseMetaData.class);
		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.getMetaData()).thenReturn(metaData);
		when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost:5432/aptech4");
		service = new DemoResetService(properties, environment, mock(JdbcTemplate.class), dataSource);
	}

	@Test
	void executeRefusesProdProfile() {
		environment.setActiveProfiles("prod");
		assertThatThrownBy(() -> service.execute("yes"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("prod");
	}

	@Test
	void planRefusesProdProfile() {
		environment.setActiveProfiles("prod");
		assertThatThrownBy(() -> service.plan())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("prod");
	}

	@Test
	void executeRefusesMissingConfirm() {
		environment.setActiveProfiles("local");
		assertThatThrownBy(() -> service.execute(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("reset-confirm");
		assertThatThrownBy(() -> service.execute("no"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("reset-confirm");
	}
}
