<script lang="ts">
	import { onMount } from 'svelte';
	import { transactionsStore, transactionStats } from '$lib/stores/transactions';
	import { formatPrice, formatDateTime, formatRelativeTime } from '$lib/utils/format';

	onMount(() => {
		transactionsStore.fetch();
	});

	function handleRefresh() {
		transactionsStore.fetch();
	}

	function getTransactionIcon(type: string) {
		return type === 'BUY' ? '🛒' : '💰';
	}

	function getTransactionColor(type: string) {
		return type === 'BUY' ? 'buy' : 'sell';
	}
</script>

<div class="page-container">
	<header class="page-header">
		<div class="header-content">
			<div>
				<h1>Transactions</h1>
				<p>Historique des transactions du marché</p>
			</div>
			<button class="refresh-btn" onclick={handleRefresh} aria-label="Actualiser">
				<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M21 2v6h-6M3 12a9 9 0 0 1 15-6.7L21 8M3 22v-6h6" />
					<path d="M21 12a9 9 0 0 1-15 6.7L3 16" />
				</svg>
			</button>
		</div>
	</header>

	<!-- Stats -->
	<div class="stats-grid">
		<div class="stat-card">
			<div class="stat-icon" style="background: rgba(99, 102, 241, 0.2)">📊</div>
			<div class="stat-content">
				<span class="stat-label">Total</span>
				<span class="stat-value">{$transactionStats.total}</span>
			</div>
		</div>
		<div class="stat-card">
			<div class="stat-icon" style="background: rgba(59, 130, 246, 0.2)">🛒</div>
			<div class="stat-content">
				<span class="stat-label">Achats</span>
				<span class="stat-value">{$transactionStats.buyCount}</span>
			</div>
		</div>
		<div class="stat-card">
			<div class="stat-icon" style="background: rgba(239, 68, 68, 0.2)">💰</div>
			<div class="stat-content">
				<span class="stat-label">Ventes</span>
				<span class="stat-value">{$transactionStats.sellCount}</span>
			</div>
		</div>
		<div class="stat-card">
			<div class="stat-icon" style="background: rgba(34, 197, 94, 0.2)">💵</div>
			<div class="stat-content">
				<span class="stat-label">Volume</span>
				<span class="stat-value">{formatPrice($transactionStats.volume)}</span>
			</div>
		</div>
	</div>

	<!-- Loading -->
	{#if $transactionsStore.loading}
		<div class="loading-container">
			<div class="spinner"></div>
			<p>Chargement des transactions...</p>
		</div>
	{:else if $transactionsStore.error}
		<div class="error-container">
			<span class="error-icon">⚠️</span>
			<div class="error-content">
				<h3>Erreur de chargement</h3>
				<p>{$transactionsStore.error}</p>
				<button class="retry-btn" onclick={handleRefresh}>Réessayer</button>
			</div>
		</div>
	{:else if $transactionsStore.transactions.length === 0}
		<div class="empty-container">
			<span class="empty-icon">📭</span>
			<h3>Aucune transaction</h3>
			<p>Aucune transaction enregistrée pour le moment</p>
		</div>
	{:else}
		<!-- Transactions Table -->
		<div class="transactions-table-container">
			<table class="transactions-table">
				<thead>
					<tr>
						<th>Type</th>
						<th>Article</th>
						<th>Quantité</th>
						<th>Prix</th>
						<th>Total</th>
						<th>Date</th>
					</tr>
				</thead>
				<tbody>
					{#each $transactionsStore.transactions as tx}
						<tr class={getTransactionColor(tx.type)}>
							<td class="type-cell">
								<span class="type-icon">{getTransactionIcon(tx.type)}</span>
								<span class="type-label">{tx.type === 'BUY' ? 'Achat' : 'Vente'}</span>
							</td>
							<td class="item-cell">{tx.item.replace(/_/g, ' ')}</td>
							<td class="amount-cell">x{tx.amount}</td>
							<td class="price-cell">{formatPrice(tx.price)}</td>
							<td class="total-cell">{formatPrice(tx.amount * tx.price)}</td>
							<td class="date-cell">
								<span class="date-relative">{formatRelativeTime(tx.timestamp)}</span>
								<span class="date-full">{formatDateTime(tx.timestamp)}</span>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
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

	.page-header {
		margin-bottom: 2rem;
	}

	.header-content {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
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

	/* Stats */
	.stats-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
		gap: 1rem;
		margin-bottom: 2rem;
	}

	.stat-card {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 1rem;
		background: var(--card-bg);
		border: 1px solid var(--border-color);
		border-radius: 10px;
	}

	.stat-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 40px;
		height: 40px;
		border-radius: 8px;
		font-size: 1.25rem;
	}

	.stat-label {
		font-size: 0.75rem;
		color: var(--text-secondary);
		display: block;
	}

	.stat-value {
		font-size: 1.1rem;
		font-weight: 700;
		color: var(--text-primary);
	}

	/* Loading */
	.loading-container {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 3rem;
		gap: 1rem;
	}

	.spinner {
		width: 32px;
		height: 32px;
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

	/* Error */
	.error-container {
		display: flex;
		align-items: center;
		gap: 1rem;
		padding: 1.5rem;
		background: var(--card-bg);
		border: 1px solid var(--error-color);
		border-radius: 10px;
	}

	.error-icon {
		font-size: 2rem;
	}

	.error-content h3 {
		margin: 0 0 0.25rem;
		color: var(--text-primary);
		font-size: 1.1rem;
	}

	.error-content p {
		margin: 0 0 0.75rem;
		color: var(--text-secondary);
	}

	.retry-btn {
		padding: 0.4rem 0.8rem;
		background: var(--accent-color);
		color: white;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		font-size: 0.9rem;
	}

	/* Empty */
	.empty-container {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 3rem;
		gap: 0.75rem;
	}

	.empty-icon {
		font-size: 2.5rem;
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

	/* Table */
	.transactions-table-container {
		background: var(--card-bg);
		border: 1px solid var(--border-color);
		border-radius: 12px;
		overflow: hidden;
	}

	.transactions-table {
		width: 100%;
		border-collapse: collapse;
	}

	.transactions-table thead {
		background: var(--bg-primary);
		border-bottom: 1px solid var(--border-color);
	}

	.transactions-table th {
		padding: 1rem;
		text-align: left;
		font-weight: 600;
		font-size: 0.875rem;
		color: var(--text-secondary);
		text-transform: uppercase;
		letter-spacing: 0.05em;
	}

	.transactions-table tbody tr {
		border-bottom: 1px solid var(--border-color);
		transition: background 0.15s ease;
	}

	.transactions-table tbody tr:hover {
		background: var(--hover-bg);
	}

	.transactions-table tbody tr:last-child {
		border-bottom: none;
	}

	.transactions-table td {
		padding: 1rem;
		color: var(--text-primary);
	}

	.type-cell {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		font-weight: 500;
	}

	.type-icon {
		font-size: 1.25rem;
	}

	.transactions-table tbody tr.buy .type-cell {
		color: #3b82f6;
	}

	.transactions-table tbody tr.sell .type-cell {
		color: #ef4444;
	}

	.item-cell {
		text-transform: capitalize;
	}

	.amount-cell {
		color: var(--text-secondary);
	}

	.price-cell {
		font-family: 'SF Mono', Monaco, monospace;
	}

	.total-cell {
		font-weight: 600;
		font-family: 'SF Mono', Monaco, monospace;
	}

	.date-cell {
		display: flex;
		flex-direction: column;
	}

	.date-relative {
		font-size: 0.875rem;
		color: var(--text-primary);
	}

	.date-full {
		font-size: 0.75rem;
		color: var(--text-muted);
	}

	@media (max-width: 768px) {
		.transactions-table-container {
			overflow-x: auto;
		}

		.transactions-table {
			min-width: 600px;
		}
	}
</style>
