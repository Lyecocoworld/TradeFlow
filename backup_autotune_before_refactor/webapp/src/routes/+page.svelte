<script lang="ts">
	import { onMount } from 'svelte';

	interface Shop {
		name: string;
		prices: number[];
		buys: number[];
		sells: number[];
		// Add other fields from your Java Shop class as needed
	}

	let loading = false;
	let shops: Shop[] = [
		{
			name: 'shop1',
			prices: [10, 12, 11],
			buys: [100, 110, 105],
			sells: [50, 55, 52],
		},
		{
			name: 'shop2',
			prices: [20, 18, 19],
			buys: [200, 190, 195],
			sells: [100, 95, 98],
		},
	];

	const formatPrice = (price: number) => {
		return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(price);
	};
</script>

<main class="p-4 md:p-8">
	<h1 class="text-3xl font-bold text-gray-800 mb-6">Auto-Tune Shops</h1>

	{#if loading}
		<p class="text-center text-gray-500">Loading shops...</p>
	{:else if shops.length === 0}
		<p class="text-center text-gray-500">No shops found.</p>
	{:else}
		<div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
			{#each shops as shop (shop.name)}
				<div class="bg-white border border-gray-200 rounded-lg shadow-md p-5 hover:shadow-lg transition-shadow">
					<h2 class="text-xl font-semibold capitalize truncate mb-2">{shop.name.replace(/_/g, ' ')}</h2>
					<p class="text-2xl font-bold text-green-600 mb-4">{formatPrice(shop.prices[shop.prices.length - 1])}</p>
					<div class="flex justify-between text-sm text-gray-600">
						<span>Buys: <span class="font-medium text-blue-500">{shop.buys[shop.buys.length - 1]}</span></span>
						<span>Sells: <span class="font-medium text-red-500">{shop.sells[shop.sells.length - 1]}</span></span>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</main>

