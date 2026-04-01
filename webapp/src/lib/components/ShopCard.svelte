<script lang="ts">
	import { formatPrice } from '$lib/utils/format';

	export let shop: {
		name: string;
		price: number;
		stock?: number;
		buyPrice?: number;
		sellPrice?: number;
		material?: string;
		category?: string;
	};

	const changePercent = shop.buyPrice && shop.sellPrice
		? ((shop.sellPrice - shop.buyPrice) / shop.buyPrice) * 100
		: 0;

	const isPositive = changePercent >= 0;

	const cardClasses = `shop-card ${isPositive ? 'positive' : 'negative'}`;

	const changeClasses = `shop-change ${isPositive ? 'positive' : 'negative'}`;

	function handleClick() {
		// Optional: handle card click
	}
</script>

<div
	class={cardClasses}
	onclick={handleClick}
	role="button"
	tabindex="0"
	onkeydown={(e) => e.key === 'Enter' && handleClick()}
>
	<div class="shop-header">
		<span class="shop-name">{shop.name.replace(/_/g, ' ')}</span>
		{#if shop.category}
			<span class="shop-category">{shop.category}</span>
		{/if}
	</div>

	<div class="shop-price">
		<span class="price-value">{formatPrice(shop.price)}</span>
		<span class="price-label">Prix actuel</span>
	</div>

	<div class="shop-stats">
		{#if shop.stock !== undefined}
			<div class="stat">
				<span class="stat-label">Stock</span>
				<span class="stat-value">{shop.stock.toLocaleString()}</span>
			</div>
		{/if}
		{#if shop.buyPrice}
			<div class="stat">
				<span class="stat-label">Achat</span>
				<span class="stat-value buy">{formatPrice(shop.buyPrice)}</span>
			</div>
		{/if}
		{#if shop.sellPrice}
			<div class="stat">
				<span class="stat-label">Vente</span>
				<span class="stat-value sell">{formatPrice(shop.sellPrice)}</span>
			</div>
		{/if}
	</div>

	{#if changePercent !== 0}
		<div class={changeClasses}>
			<span class="change-icon">{isPositive ? '▲' : '▼'}</span>
			<span class="change-value">{Math.abs(changePercent).toFixed(2)}%</span>
		</div>
	{/if}
</div>

<style>
	.shop-card {
		background: var(--card-bg);
		border: 1px solid var(--border-color);
		border-radius: 12px;
		padding: 1.25rem;
		cursor: pointer;
		transition: all 0.2s ease;
		position: relative;
		overflow: hidden;
	}

	.shop-card:hover {
		transform: translateY(-2px);
		box-shadow: 0 8px 25px var(--shadow-color);
		border-color: var(--accent-color);
	}

	.shop-card:focus-visible {
		outline: 2px solid var(--accent-color);
		outline-offset: 2px;
	}

	.shop-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 1rem;
	}

	.shop-name {
		font-weight: 600;
		font-size: 1.1rem;
		color: var(--text-primary);
		text-transform: capitalize;
	}

	.shop-category {
		font-size: 0.75rem;
		padding: 0.25rem 0.5rem;
		background: var(--accent-color);
		color: white;
		border-radius: 999px;
		text-transform: uppercase;
		font-weight: 600;
		letter-spacing: 0.05em;
	}

	.shop-price {
		text-align: center;
		padding: 1rem 0;
		margin-bottom: 1rem;
		border-top: 1px solid var(--border-color);
		border-bottom: 1px solid var(--border-color);
	}

	.price-value {
		display: block;
		font-size: 1.75rem;
		font-weight: 700;
		color: var(--accent-color);
	}

	.price-label {
		font-size: 0.875rem;
		color: var(--text-secondary);
	}

	.shop-stats {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(80px, 1fr));
		gap: 0.75rem;
	}

	.stat {
		text-align: center;
	}

	.stat-label {
		display: block;
		font-size: 0.75rem;
		color: var(--text-secondary);
		margin-bottom: 0.25rem;
	}

	.stat-value {
		display: block;
		font-weight: 600;
		color: var(--text-primary);
	}

	.stat-value.buy {
		color: #3b82f6;
	}

	.stat-value.sell {
		color: #ef4444;
	}

	.shop-change {
		position: absolute;
		top: 1rem;
		right: 1rem;
		display: flex;
		align-items: center;
		gap: 0.25rem;
		padding: 0.25rem 0.5rem;
		border-radius: 6px;
		font-size: 0.75rem;
		font-weight: 600;
	}

	.shop-change.positive {
		background: rgba(34, 197, 94, 0.2);
		color: #22c55e;
	}

	.shop-change.negative {
		background: rgba(239, 68, 68, 0.2);
		color: #ef4444;
	}

	:global(.dark) .shop-card {
		background: #1e293b;
	}
</style>
