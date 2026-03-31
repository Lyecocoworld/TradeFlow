/**
 * UI Store - Svelte stores for SSR compatibility
 */

import { writable, derived } from 'svelte/store';
import { browser } from '$app/environment';

interface Notification {
	id: string;
	message: string;
	type: 'success' | 'error' | 'warning' | 'info';
	duration?: number;
}

interface UIState {
	theme: 'light' | 'dark' | 'auto';
	sidebarOpen: boolean;
	notifications: Notification[];
	currentPage: 'dashboard' | 'shops' | 'transactions' | 'settings';
}

const savedTheme = browser
	? (localStorage.getItem('tradeflow_theme') as UIState['theme']) || 'auto'
	: 'auto';

const initialState: UIState = {
	theme: savedTheme,
	sidebarOpen: true,
	notifications: [],
	currentPage: 'shops', // Default to shops page
};

function createUIStore() {
	const { subscribe, update, set } = writable<UIState>({ ...initialState });

	return {
		subscribe,

		get theme() {
			let currentValue: UIState['theme'];
			subscribe((value) => (currentValue = value.theme))();
			return currentValue;
		},

		get sidebarOpen() {
			let currentValue: boolean;
			subscribe((value) => (currentValue = value.sidebarOpen))();
			return currentValue;
		},

		get notifications() {
			let currentValue: Notification[];
			subscribe((value) => (currentValue = value.notifications))();
			return currentValue;
		},

		get currentPage() {
			let currentValue: UIState['currentPage'];
			subscribe((value) => (currentValue = value.currentPage))();
			return currentValue;
		},

		toggleSidebar() {
			update((state) => ({ ...state, sidebarOpen: !state.sidebarOpen }));
		},

		setTheme(theme: UIState['theme']) {
			if (browser) {
				localStorage.setItem('tradeflow_theme', theme);
			}
			update((state) => ({ ...state, theme }));
		},

		notify(message: string, type: Notification['type'] = 'info', duration = 3000) {
			const id = Math.random().toString(36).substring(7);
			const notification: Notification = { id, message, type, duration };

			update((state) => ({
				...state,
				notifications: [...state.notifications, notification],
			}));

			// Auto-remove after duration
			if (duration > 0) {
				setTimeout(() => this.removeNotification(id), duration);
			}

			return id;
		},

		removeNotification(id: string) {
			update((state) => ({
				...state,
				notifications: state.notifications.filter((n) => n.id !== id),
			}));
		},

		clearNotifications() {
			update((state) => ({ ...state, notifications: [] }));
		},

		setPage(page: UIState['currentPage']) {
			update((state) => ({ ...state, currentPage: page }));
		},

		// Method to get current state value (useful for SSR)
		get() {
			let currentValue: UIState;
			subscribe((value) => (currentValue = value))();
			return currentValue;
		}
	};
}

export const uiStore = createUIStore();

// Derived effective theme
export const effectiveTheme = derived(
	[uiStore],
	([$uiStore]) => {
		if ($uiStore.theme !== 'auto') return $uiStore.theme;
		if (browser) {
			return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
		}
		return 'light';
	}
);
