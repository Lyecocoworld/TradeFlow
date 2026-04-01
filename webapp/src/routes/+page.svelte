<script lang="ts">
	import { onMount } from 'svelte';
	import { shopsStore } from '$lib/stores/shops';
	import { uiStore } from '$lib/stores/ui';
	import { formatPrice } from '$lib/utils/format';
	import SearchBar from '$lib/components/SearchBar.svelte';
	import ShopCard from '$lib/components/ShopCard.svelte';

	onMount(() => {
		shopsStore.fetch();
	});

	let searchQuery = '';

	function handleSearch(e: CustomEvent<{ value: string }>) {
		searchQuery = e.detail.value;
		shopsStore.setSearch(searchQuery);
	}

	function handleRefresh() {
		shopsStore.fetch();
		uiStore.notify('Données actualisées', 'success');
	}

	function prevPage() {
		shopsStore.prevPage();
	}

	function nextPage() {
		shopsStore.nextPage();
	}

	function formatValue(value: number) {
		if (!value || value < 0) return '-';
		return formatPrice(value);
	}
</script>

<div class="page-container">
	<!-- Header -->
	<header class="page-header">
		<div class="header-content">
			<div>
				<h1>Boutiques</h1>
				<p>Consultez les prix du marché en temps réel</p>
			</div>
			<button class="refresh-btn" onclick={handleRefresh} aria-label="Actualiser">
				<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M21 2v6h-6M3 12a9 9 0 0 1 15-6.7L21 8M3 22v-6h6" />
					<path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
				</svg>
			</button>
		</div>

		<div class="header-actions">
			<SearchBar placeholder="Rechercher un article..." bind:value={searchQuery} on:search={handleSearch} />
		</div>
	</header>

	<!-- Stats Cards -->
	<div class="stats-grid">
		<div class="stat-card">
			<div class="stat-icon" style="background: rgba(99, 102, 241, 0.2)">📦</div>
			<div class="stat-content">
				<span class="stat-label">Total Articles</span>
				<span class="stat-value">{$shopsStore.total}</span>
			</div>
		</div>
		<div class="stat-card">
			<div class="stat-icon" style="background: rgba(34, 197, 94, 0.2)">💰</div>
			<div class="stat-content">
				<span class="stat-label">Affichés</span>
				<span class="stat-value">{$shopsStore.filteredShops.length}</span>
			</div>
		</div>
	</div>

	<!-- Loading State -->
	{#if $shopsStore.loading}
		<div class="loading-container">
			<div class="spinner"></div>
			<p>Chargement des boutiques...</p>
		</div>
	{:else if $shopsStore.error}
		<div class="error-container">
			<span class="error-icon">⚠️</span>
			<div class="error-content">
				<h3>Erreur de chargement</h3>
				<p>{$shopsStore.error}</p>
				<button class="retry-btn" onclick={handleRefresh}>Réessayer</button>
			</div>
		</div>
	{:else if $shopsStore.filteredShops.length === 0}
		<div class="empty-container">
			<span class="empty-icon">🔍</span>
			<h3>Aucun résultat</h3>
			<p>Essayez une autre recherche</p>
		</div>
	{:else}
		<!-- Shops Grid -->
		<div class="shops-grid">
			{#each $shopsStore.filteredShops as shop (shop.name || shop.id)}
				<ShopCard {shop} />
			{/each}
		</div>

		<!-- Pagination -->
		{#if $shopsStore.total > $shopsStore.limit}
			<div class="pagination">
				<button class="pagination-btn" disabled={$shopsStore.page === 1} onclick={prevPage}>
					← Précédent
				</button>
				<span class="pagination-info"> Page {$shopsStore.page} sur {Math.ceil($shopsStore.total / $shopsStore.limit)} </span>
				<button class="pagination-btn" disabled={$shopsStore.page >= Math.ceil($shopsStore.total / $shopsStore.limit)} onclick={nextPage}>
					Suivant →
				</button>
			</div>
		{/if}
	{/if}
</div>

<style>
	.page-container {
		animation: fadeIn 0.3s ease;
	}

	@keyframes fadeIn {
		from {
			opacity: 0;
			transform: translateY(10px);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}

	/* Header */
	.page-header {
		margin-bottom: 2rem;
	}

	.header-content {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		margin-bottom: 1rem;
	}

	.header-content h1 {
		margin: 0;
		font-size: 2rem;
		font-weight: 700;
		color: var(--text-primary);
	}

	.header-content p {
		margin: 0.25rem 0 0;
		color: var(--text-secondary);
	}

	.refresh-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 40px;
		height: 40px;
		border: 1px solid var(--border-color);
		background: var(--card-bg);
		border-radius: 8px;
		cursor: pointer;
		color: var(--text-secondary);
		transition: all 0.2s ease;
	}

	.refresh-btn:hover {
		background: var(--accent-color);
		border-color: var(--accent-color);
		color: white;
		transform: rotate(180deg);
	}

	.header-actions {
		display: flex;
		gap: 1rem;
		align-items: center;
	}

	/* Stats Grid */
	.stats-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
		gap: 1rem;
		margin-bottom: 2rem;
	}

	.stat-card {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 1.25rem;
		background: var(--card-bg);
		border: 1px solid var(--border-color);
		border-radius: 12px;
		transition: all 0.2s ease;
	}

	.stat-card:hover {
		border-color: var(--accent-color);
		box-shadow: 0 4px 12px var(--shadow-color);
	}

	.stat-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 48px;
		height: 48px;
		border-radius: 10px;
		font-size: 1.5rem;
	}

	.stat-content {
		display: flex;
		flex-direction: column;
	}

	.stat-label {
		font-size: 0.875rem;
		color: var(--text-secondary);
	}

	.stat-value {
		font-size: 1.25rem;
		font-weight: 700;
		color: var(--text-primary);
	}

	/* Loading State */
	.loading-container {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 4rem 2rem;
		gap: 1rem;
	}

	.spinner {
		width: 40px;
		height: 40px;
		border: 3px solid var(--border-color);
		border-top-color: var(--accent-color);
		border-radius: 50%;
		animation: spin 1s linear infinite;
	}

	@keyframes spin {
		to {
			transform: rotate(360deg);
		}
	}

	.loading-container p {
		color: var(--text-secondary);
	}

	/* Error State */
	.error-container {
		display: flex;
		align-items: center;
		gap: 1.5rem;
		padding: 2rem;
		background: var(--card-bg);
		border: 1px solid var(--error-color);
		border-radius: 12px;
	}

	.error-icon {
		font-size: 2.5rem;
	}

	.error-content h3 {
		margin: 0 0 0.5rem;
		color: var(--text-primary);
	}

	.error-content p {
		margin: 0 0 1rem;
		color: var(--text-secondary);
	}

	.retry-btn {
		padding: 0.5rem 1rem;
		background: var(--accent-color);
		color: white;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		font-weight: 500;
		transition: background 0.2s ease;
	}

	.retry-btn:hover {
		background: var(--accent-hover);
	}

	/* Empty State */
	.empty-container {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 4rem 2rem;
		gap: 1rem;
	}

	.empty-icon {
		font-size: 3rem;
		opacity: 0.5;
	}

	.empty-container h3 {
		margin: 0;
		color: var(--text-primary);
	}

	.empty-container p {
		margin: 0;
		color: var(--text-secondary);
	}

	/* Shops Grid */
	.shops-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
		gap: 1.25rem;
	}

	@media (max-width: 640px) {
		.shops-grid {
			grid-template-columns: 1fr;
		}
	}

	/* Pagination */
	.pagination {
		display: flex;
		justify-content: center;
		align-items: center;
		gap: 1rem;
		margin-top: 2rem;
		padding: 1rem;
	}

	.pagination-btn {
		padding: 0.625rem 1.25rem;
		background: var(--card-bg);
		border: 1px solid var(--border-color);
		border-radius: 8px;
		cursor: pointer;
		font-weight: 500;
		color: var(--text-primary);
		transition: all 0.2s ease;
	}

	.pagination-btn:hover:not(:disabled) {
		background: var(--accent-color);
		border-color: var(--accent-color);
		color: white;
	}

	.pagination-btn:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}

	.pagination-info {
		color: var(--text-secondary);
		font-size: 0.9rem;
	}
</style>
