<script lang="ts">
	import '../app.css';
	import { uiStore, effectiveTheme } from '$lib/stores/ui';
	import favicon from '$lib/assets/favicon.svg';
	import type { Notification } from '$lib/stores/ui';

	let { children } = $props();

	const navItems = [
		{ id: 'dashboard', label: 'Tableau de bord', icon: '📊' },
		{ id: 'shops', label: 'Boutiques', icon: '🏪' },
		{ id: 'transactions', label: 'Transactions', icon: '📜' },
		{ id: 'settings', label: 'Paramètres', icon: '⚙️' },
	];

	function setPage(page: 'dashboard' | 'shops' | 'transactions' | 'settings') {
		uiStore.setPage(page);
	}

	function toggleTheme() {
		const currentTheme = $uiStore.theme;
		uiStore.setTheme(currentTheme === 'dark' ? 'light' : 'dark');
	}

	function removeNotification(id: string) {
		uiStore.removeNotification(id);
	}

	function getNotificationClass(type: Notification['type']) {
		return `notification notification-${type.toLowerCase()}`;
	}

	// Apply theme to document
	$effect(() => {
		const effective = $effectiveTheme;
		if (effective === 'dark') {
			document.documentElement.classList.add('dark');
		} else {
			document.documentElement.classList.remove('dark');
		}
	});
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>TradeFlow - Économie Serveur</title>
	<meta name="description" content="TradeFlow - Interface d'économie de serveur Minecraft" />
</svelte:head>

<div class="app-container">
	<!-- Navigation -->
	<nav class="navbar">
		<div class="nav-brand">
			<span class="brand-icon">💰</span>
			<span class="brand-name">TradeFlow</span>
		</div>

		<div class="nav-links">
			{#each navItems as item}
				<button
					class="nav-link"
					class:active={$uiStore.currentPage === item.id}
					onclick={() => setPage(item.id as any)}
				>
					<span class="nav-icon">{item.icon}</span>
					<span class="nav-label">{item.label}</span>
				</button>
			{/each}
		</div>

		<div class="nav-actions">
			<button class="theme-toggle" onclick={toggleTheme} aria-label="Toggle theme">
				{$uiStore.theme === 'dark' ? '☀️' : '🌙'}
			</button>
		</div>
	</nav>

	<!-- Notifications -->
	<div class="notifications">
		{#each $uiStore.notifications as notification (notification.id)}
			<div class={getNotificationClass(notification.type)}>
				<span class="notification-message">{notification.message}</span>
				<button class="notification-close" onclick={() => removeNotification(notification.id)} aria-label="Close">
					✕
				</button>
			</div>
		{/each}
	</div>

	<!-- Main Content -->
	<main class="main-content">
		{@render children()}
	</main>

	<!-- Footer -->
	<footer class="footer">
		<p>TradeFlow Web Interface &copy; 2024</p>
		<p class="footer-status">
			<span class="status-dot"></span>
			<span>Connecté</span>
		</p>
	</footer>
</div>

<style>
	:global(*) {
		--accent-color: #6366f1;
		--accent-hover: #4f46e5;
		--success-color: #22c55e;
		--warning-color: #f59e0b;
		--error-color: #ef4444;

		/* Light theme */
		--bg-primary: #f8fafc;
		--bg-secondary: #ffffff;
		--card-bg: #ffffff;
		--text-primary: #1e293b;
		--text-secondary: #64748b;
		--text-muted: #94a3b8;
		--border-color: #e2e8f0;
		--input-bg: #f1f5f9;
		--hover-bg: #f1f5f9;
		--shadow-color: rgba(0, 0, 0, 0.1);
	}

	:global(.dark) {
		--bg-primary: #0f172a;
		--bg-secondary: #1e293b;
		--card-bg: #1e293b;
		--text-primary: #f1f5f9;
		--text-secondary: #94a3b8;
		--text-muted: #64748b;
		--border-color: #334155;
		--input-bg: #0f172a;
		--hover-bg: #334155;
		--shadow-color: rgba(0, 0, 0, 0.3);
	}

	:global(body) {
		margin: 0;
		font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell,
			'Open Sans', 'Helvetica Neue', sans-serif;
		background: var(--bg-primary);
		color: var(--text-primary);
		line-height: 1.6;
		transition: background-color 0.3s ease, color 0.3s ease;
	}

	.app-container {
		min-height: 100vh;
		display: flex;
		flex-direction: column;
	}

	/* Navigation */
	.navbar {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0.75rem 1.5rem;
		background: var(--card-bg);
		border-bottom: 1px solid var(--border-color);
		position: sticky;
		top: 0;
		z-index: 100;
		transition: all 0.3s ease;
	}

	.nav-brand {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		font-weight: 700;
		font-size: 1.25rem;
		color: var(--text-primary);
	}

	.brand-icon {
		font-size: 1.5rem;
	}

	.nav-links {
		display: flex;
		gap: 0.5rem;
	}

	.nav-link {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.5rem 1rem;
		border: none;
		background: transparent;
		color: var(--text-secondary);
		border-radius: 8px;
		cursor: pointer;
		font-weight: 500;
		transition: all 0.2s ease;
	}

	.nav-link:hover {
		background: var(--hover-bg);
		color: var(--text-primary);
	}

	.nav-link.active {
		background: var(--accent-color);
		color: white;
	}

	.nav-icon {
		font-size: 1.25rem;
	}

	.nav-label {
		display: none;
	}

	@media (min-width: 640px) {
		.nav-label {
			display: inline;
		}
	}

	.nav-actions {
		display: flex;
		gap: 0.5rem;
	}

	.theme-toggle {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 40px;
		height: 40px;
		border: 1px solid var(--border-color);
		background: var(--bg-primary);
		border-radius: 8px;
		cursor: pointer;
		font-size: 1.25rem;
		transition: all 0.2s ease;
	}

	.theme-toggle:hover {
		background: var(--hover-bg);
		border-color: var(--accent-color);
	}

	/* Notifications */
	.notifications {
		position: fixed;
		top: 5rem;
		right: 1rem;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		z-index: 200;
		max-width: 400px;
		width: 100%;
		padding: 0 1rem;
		pointer-events: none;
	}

	.notification {
		pointer-events: auto;
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 0.75rem 1rem;
		background: var(--card-bg);
		border: 1px solid var(--border-color);
		border-radius: 8px;
		box-shadow: 0 4px 12px var(--shadow-color);
		animation: slideIn 0.3s ease;
	}

	@keyframes slideIn {
		from {
			transform: translateX(100%);
			opacity: 0;
		}
		to {
			transform: translateX(0);
			opacity: 1;
		}
	}

	.notification-success {
		border-left: 4px solid var(--success-color);
	}

	.notification-error {
		border-left: 4px solid var(--error-color);
	}

	.notification-warning {
		border-left: 4px solid var(--warning-color);
	}

	.notification-info {
		border-left: 4px solid var(--accent-color);
	}

	.notification-message {
		flex: 1;
		font-size: 0.9rem;
	}

	.notification-close {
		background: transparent;
		border: none;
		color: var(--text-secondary);
		cursor: pointer;
		padding: 0.25rem;
		font-size: 0.875rem;
	}

	.notification-close:hover {
		color: var(--text-primary);
	}

	/* Main Content */
	.main-content {
		flex: 1;
		max-width: 1400px;
		width: 100%;
		margin: 0 auto;
		padding: 1.5rem;
	}

	/* Footer */
	.footer {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 1rem 1.5rem;
		background: var(--card-bg);
		border-top: 1px solid var(--border-color);
		font-size: 0.875rem;
		color: var(--text-secondary);
	}

	.footer-status {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}

	.status-dot {
		width: 8px;
		height: 8px;
		background: var(--success-color);
		border-radius: 50%;
		animation: pulse 2s infinite;
	}

	@keyframes pulse {
		0%,
		100% {
			opacity: 1;
		}
		50% {
			opacity: 0.5;
		}
	}
</style>
