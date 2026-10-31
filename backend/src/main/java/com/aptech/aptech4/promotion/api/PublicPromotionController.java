package com.aptech.aptech4.promotion.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aptech.aptech4.promotion.application.PublicPromotionService;
import com.aptech.aptech4.promotion.domain.PromotionPlacementKey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/promotions")
@Tag(name = "Public Promotions", description = "Effective homepage and placement creatives (UTC schedule filter)")
@SecurityRequirements({})
public class PublicPromotionController {

	private final PublicPromotionService publicPromotionService;

	public PublicPromotionController(PublicPromotionService publicPromotionService) {
		this.publicPromotionService = publicPromotionService;
	}

	@GetMapping("/homepage")
	@Operation(summary = "Effective homepage placements (hero + promo slots); empty when none — FE FoodMart fallback")
	public ResponseEntity<HomepagePromotionsResponse> homepage() {
		return ResponseEntity.ok(publicPromotionService.homepage());
	}

	@GetMapping("/placements")
	@Operation(summary = "Effective placements filtered by optional placement keys")
	public ResponseEntity<List<PublicPlacementResponse>> placements(
			@RequestParam(required = false) List<PromotionPlacementKey> keys
	) {
		return ResponseEntity.ok(publicPromotionService.listPlacements(keys));
	}
}
