package com.aptech.aptech4.promotion.domain;

import java.util.UUID;

import com.aptech.aptech4.common.domain.AbstractUuidEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "promotion_strategy_translations",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_promo_strategy_translations_strategy_locale",
				columnNames = { "strategy_id", "locale" }
		)
)
public class PromotionStrategyTranslation extends AbstractUuidEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "strategy_id", nullable = false)
	private PromotionStrategy strategy;

	@Column(name = "locale", nullable = false, length = 5)
	private String locale;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	protected PromotionStrategyTranslation() {
	}

	public static PromotionStrategyTranslation createNew(PromotionStrategy strategy, String locale, String name) {
		PromotionStrategyTranslation row = new PromotionStrategyTranslation();
		row.strategy = strategy;
		row.locale = locale;
		row.name = name;
		return row;
	}

	@Override
	public UUID getId() {
		return id;
	}

	public PromotionStrategy getStrategy() {
		return strategy;
	}

	public String getLocale() {
		return locale;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
