/**
 * API Service for TradeFlow Web Interface
 * Handles all communication with the backend API
 */

import { browser } from '$app/environment';

const API_BASE = browser ? window.location.origin : '';

interface Shop {
	id: string;
	name: string;
	price: number;
	stock: number;
	buyPrice?: number;
	sellPrice?: number;
	category?: string;
	material?: string;
	[key: string]: any;
}

interface Transaction {
	id: string;
	playerUuid: string;
	item: string;
	amount: number;
	price: number;
	type: 'BUY' | 'SELL';
	timestamp: number;
}

interface HealthStatus {
	healthy: boolean;
	shopCount: number;
	timestamp: number;
}

interface ApiResponse<T> {
	data?: T;
	error?: string;
	status: number;
}

interface ShopsResponse {
	shops: Shop[];
	total: number;
}

/**
 * API Client with error handling and type safety
 */
class ApiService {
	private apiKey: string | null = null;

	constructor() {
		// API key can be set from localStorage or config
		if (browser) {
			this.apiKey = localStorage.getItem('tradeflow_api_key') || null;
		}
	}

	setApiKey(key: string) {
		this.apiKey = key;
		if (browser) {
			localStorage.setItem('tradeflow_api_key', key);
		}
	}

	private async fetch<T>(endpoint: string, options?: RequestInit): Promise<ApiResponse<T>> {
		const url = new URL(endpoint, API_BASE);
		if (this.apiKey) {
			url.searchParams.set('key', this.apiKey);
		}

		try {
			const response = await fetch(url.toString(), {
				...options,
				headers: {
					'Content-Type': 'application/json',
					...options?.headers,
				},
			});

			const status = response.status;

			if (!response.ok) {
				const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
				return { error: errorData.error || response.statusText, status };
			}

			const data = await response.json();
			return { data, status };
		} catch (error) {
			return {
				error: error instanceof Error ? error.message : 'Network error',
				status: 0,
			};
		}
	}

	/**
	 * Get all shops with optional filtering
	 */
	async getShops(params: {
		search?: string;
		page?: number;
		limit?: number;
	} = {}): Promise<ApiResponse<ShopsResponse>> {
		const searchParams = new URLSearchParams();
		if (params.search) searchParams.set('search', params.search);
		if (params.page) searchParams.set('page', params.page.toString());
		if (params.limit) searchParams.set('limit', params.limit.toString());

		const endpoint = `/api/shops${searchParams.toString() ? '?' + searchParams.toString() : ''}`;
		return this.fetch<ShopsResponse>(endpoint);
	}

	/**
	 * Get transactions with optional filtering
	 */
	async getTransactions(params: {
		player?: string;
		limit?: number;
	} = {}): Promise<ApiResponse<Transaction[]>> {
		const searchParams = new URLSearchParams();
		if (params.player) searchParams.set('player', params.player);
		if (params.limit) searchParams.set('limit', params.limit.toString());

		const endpoint = `/api/transactions${searchParams.toString() ? '?' + searchParams.toString() : ''}`;
		return this.fetch<Transaction[]>(endpoint);
	}

	/**
	 * Get health status
	 */
	async getHealth(): Promise<ApiResponse<HealthStatus>> {
		return this.fetch<HealthStatus>('/api/health');
	}

	/**
	 * Trigger price recalculation (admin only)
	 */
	async recalculatePrices(): Promise<ApiResponse<{ message: string }>> {
		return this.fetch<{ message: string }>('/api/recalculate', {
			method: 'POST',
		});
	}

	/**
	 * Get shop by name
	 */
	async getShop(name: string): Promise<ApiResponse<Shop | null>> {
		const result = await this.getShops({ search: name });
		if (result.error || !result.data) {
			return { error: result.error, status: result.status };
		}
		const shop = result.data.shops.find((s) => s.name === name);
		return { data: shop || null, status: result.status };
	}
}

// Singleton instance
export const api = new ApiService();
export type { Shop, Transaction, HealthStatus, ShopsResponse };
