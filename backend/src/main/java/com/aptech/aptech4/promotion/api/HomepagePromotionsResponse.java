package com.aptech.aptech4.promotion.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "HomepagePromotionsResponse")
public record HomepagePromotionsResponse(
		List<PublicPlacementResponse> hero,
		PublicPlacementResponse promoTop,
		PublicPlacementResponse promoBottom
) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static HomepagePromotionsResponse empty() {
		return new HomepagePromotionsResponse(List.of(), null, null);
	}
}
