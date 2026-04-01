/**
 * Transactions Store - Svelte stores for SSR compatibility
 */

import { writable, derived, get } from 'svelte/store';
import { api, type Transaction } from '$lib/services/api';

interface TransactionsState {
	transactions: Transaction[];
	loading: boolean;
	error: string | null;
	playerFilter: string | null;
	limit: number;
}

const initialState: TransactionsState = {
	transactions: [],
	loading: false,
	error: null,
	playerFilter: null,
	limit: 100,
};

function createTransactionsStore() {
	const { subscribe, update, set } = writable<TransactionsState>({ ...initialState });

	return {
		subscribe,

		get transactions() {
			return get(this).transactions;
		},

		get loading() {
			return get(this).loading;
		},

		get error() {
			return get(this).error;
		},

		async fetch() {
			update((state) => ({ ...state, loading: true, error: null }));
			const currentState = get(this);
			const result = await api.getTransactions({
				player: currentState.playerFilter || undefined,
				limit: currentState.limit,
			});

			if (result.error) {
				update((state) => ({
					...state,
					loading: false,
					error: result.error || 'Failed to fetch transactions',
				}));
				return;
			}

			if (result.data) {
				update((state) => ({
					...state,
					transactions: result.data!,
					loading: false,
					error: null,
				}));
			}
		},

		setPlayer(playerId: string | null) {
			update((state) => ({ ...state, playerFilter: playerId }));
			this.fetch();
		},

		setLimit(limit: number) {
			update((state) => ({ ...state, limit: Math.min(limit, 1000) }));
			this.fetch();
		},

		reset() {
			set({ ...initialState });
		},

		get() {
			let currentValue: TransactionsState;
			subscribe((value) => (currentValue = value))();
			return currentValue;
		}
	};
}

export const transactionsStore = createTransactionsStore();

// Derived stats
export const transactionStats = derived(transactionsStore, ($transactionsStore) => ({
	get total() {
		return $transactionsStore.transactions.length;
	},
	get buyCount() {
		return $transactionsStore.transactions.filter((t) => t.type === 'BUY').length;
	},
	get sellCount() {
		return $transactionsStore.transactions.filter((t) => t.type === 'SELL').length;
	},
	get volume() {
		return $transactionsStore.transactions.reduce((sum, t) => sum + t.amount * t.price, 0);
	}
}));
