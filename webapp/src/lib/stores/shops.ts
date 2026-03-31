/**
 * Shops Store - Svelte stores for SSR compatibility
 */

import { writable, derived, get } from 'svelte/store';
import { api, type Shop } from '$lib/services/api';

interface ShopsState {
	shops: Shop[];
	filteredShops: Shop[];
	loading: boolean;
	error: string | null;
	searchQuery: string;
	category: string | null;
	total: number;
	page: number;
	limit: number;
}

const initialState: ShopsState = {
	shops: [],
	filteredShops: [],
	loading: false,
	error: null,
	searchQuery: '',
	category: null,
	total: 0,
	page: 1,
	limit: 50,
};

function createShopsStore() {
	const { subscribe, update, set } = writable<ShopsState>({ ...initialState });

	return {
		subscribe,

		get shops() {
			return get(this).shops;
		},

		get filteredShops() {
			return get(this).filteredShops;
		},

		get loading() {
			return get(this).loading;
		},

		get error() {
			return get(this).error;
		},

		get total() {
			return get(this).total;
		},

		get page() {
			return get(this).page;
		},

		get limit() {
			return get(this).limit;
		},

		async fetch() {
			update((state) => ({ ...state, loading: true, error: null }));
			const currentState = get(this);
			const result = await api.getShops({
				search: currentState.searchQuery || undefined,
				page: currentState.page,
				limit: currentState.limit,
			});

			if (result.error) {
				update((state) => ({
					...state,
					loading: false,
					error: result.error || 'Failed to fetch shops',
				}));
				return;
			}

			if (result.data) {
				update((state) => ({
					...state,
					shops: result.data!.shops,
					filteredShops: result.data!.shops,
					total: result.data!.total,
					loading: false,
					error: null,
				}));
			}
		},

		setSearch(query: string) {
			update((state) => ({ ...state, searchQuery: query, page: 1 }));
			this.fetch();
		},

		setCategory(category: string | null) {
			update((state) => ({ ...state, category, page: 1 }));
			this.applyFilters();
		},

		applyFilters() {
			const currentState = get(this);
			let filtered = [...currentState.shops];

			if (currentState.category) {
				filtered = filtered.filter((shop) => shop.category === currentState.category);
			}

			if (currentState.searchQuery) {
				const query = currentState.searchQuery.toLowerCase();
				filtered = filtered.filter(
					(shop) =>
						shop.name.toLowerCase().includes(query) ||
						shop.material?.toLowerCase().includes(query)
				);
			}

			update((state) => ({ ...state, filteredShops: filtered }));
		},

		nextPage() {
			const currentState = get(this);
			const maxPage = Math.ceil(currentState.total / currentState.limit);
			const newPage = Math.min(currentState.page + 1, maxPage);
			update((state) => ({ ...state, page: newPage }));
			this.fetch();
		},

		prevPage() {
			const currentState = get(this);
			const newPage = Math.max(currentState.page - 1, 1);
			update((state) => ({ ...state, page: newPage }));
			this.fetch();
		},

		reset() {
			set({ ...initialState });
		},

		get() {
			let currentValue: ShopsState;
			subscribe((value) => (currentValue = value))();
			return currentValue;
		}
	};
}

export const shopsStore = createShopsStore();

// Derived categories
export const categories = derived(shopsStore, ($shopsStore) => {
	const cats = new Set<string>();
	$shopsStore.shops.forEach((shop) => {
		if (shop.category) cats.add(shop.category);
	});
	return Array.from(cats).sort();
});
