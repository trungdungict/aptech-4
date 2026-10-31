package com.aptech.aptech4.catalog.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aptech.aptech4.catalog.upload.ProductImageUploadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/admin/uploads")
@Tag(name = "Admin product uploads")
@SecurityRequirement(name = "bearer-jwt")
public class AdminProductImageUploadController {

	private final ProductImageUploadService uploadService;

	public AdminProductImageUploadController(ProductImageUploadService uploadService) {
		this.uploadService = uploadService;
	}

	@PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Upload a product image (ADMIN). Returns a public relative path for product imageUrl.")
	public ResponseEntity<ProductImageUploadResponse> upload(@RequestPart("file") MultipartFile file) {
		return ResponseEntity.ok(uploadService.upload(file));
	}
}
