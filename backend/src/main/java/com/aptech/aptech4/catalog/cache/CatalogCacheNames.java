package com.aptech.aptech4.catalog.cache;

/**
 * Spring Cache region names for public catalog (ADR-015).
 */
public final class CatalogCacheNames {

	public static final String CATEGORIES_LIST = "catalog:categories:list";
	public static final String CATEGORIES_DETAIL = "catalog:categories:detail";
	public static final String PRODUCTS_LIST = "catalog:products:list";
	public static final String PRODUCTS_DETAIL = "catalog:products:detail";

	private CatalogCacheNames() {
	}
}
