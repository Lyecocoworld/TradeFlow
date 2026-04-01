<script lang="ts">
	import { createEventDispatcher } from 'svelte';
	import { debounce } from '$lib/utils/debounce';

	export let placeholder = 'Rechercher...';
	export let value = '';
	const dispatch = createEventDispatcher();

	const input = debounce((val: string) => {
		dispatch('search', { value: val });
	}, 300);

	function handleChange(e: Event) {
		const target = e.target as HTMLInputElement;
		value = target.value;
		input(value);
	}

	function handleClear() {
		value = '';
		input('');
	}
</script>

<div class="search-bar">
	<div class="search-icon">
		<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
			<circle cx="11" cy="11" r="8" />
			<path d="m21 21-4.35-4.35" />
		</svg>
	</div>
	<input
		type="text"
		{placeholder}
		bind:value
		oninput={handleChange}
		class="search-input"
	/>
	{#if value}
		<button class="clear-btn" onclick={handleClear} aria-label="Clear search">
			<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<path d="M18 6 6 6M6 6l12 12" />
			</svg>
		</button>
	{/if}
</div>

<style>
	.search-bar {
		position: relative;
		display: flex;
		align-items: center;
		background: var(--input-bg);
		border: 1px solid var(--border-color);
		border-radius: 8px;
		padding: 0 0.75rem;
		transition: all 0.2s ease;
	}

	.search-bar:focus-within {
		border-color: var(--accent-color);
		box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
	}

	.search-icon {
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--text-secondary);
		margin-right: 0.5rem;
	}

	.search-input {
		flex: 1;
		border: none;
		background: transparent;
		padding: 0.75rem 0;
		font-size: 0.95rem;
		color: var(--text-primary);
		outline: none;
	}

	.search-input::placeholder {
		color: var(--text-muted);
	}

	.clear-btn {
		display: flex;
		align-items: center;
		justify-content: center;
		background: transparent;
		border: none;
		padding: 0.25rem;
		margin-left: 0.5rem;
		color: var(--text-secondary);
		cursor: pointer;
		border-radius: 4px;
		transition: all 0.15s ease;
	}

	.clear-btn:hover {
		background: var(--hover-bg);
		color: var(--text-primary);
	}

	:global(.dark) .search-bar {
		background: #1e293b;
	}
</style>
