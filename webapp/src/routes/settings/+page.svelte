<script lang="ts">
	import { api } from '$lib/services/api';
	import { uiStore, effectiveTheme } from '$lib/stores/ui';
	import { browser } from '$app/environment';

	let apiKey = browser ? localStorage.getItem('tradeflow_api_key') || '' : '';
	let refreshInterval = browser ? parseInt(localStorage.getItem('tradeflow_refresh_interval') || '30') : 30;
	let showApiKey = false;

	const apiUrl = browser ? window.location.origin + '/api' : '/api';

	function saveApiKey() {
		if (apiKey) {
			api.setApiKey(apiKey);
			uiStore.notify('Clé API enregistrée', 'success');
		} else {
			api.setApiKey('');
			uiStore.notify('Clé API supprimée', 'info');
		}
	}

	function saveSettings() {
		if (browser) {
			localStorage.setItem('tradeflow_refresh_interval', refreshInterval.toString());
		}
		uiStore.notify('Paramètres sauvegardés', 'success');
	}

	function toggleTheme() {
		const newTheme = $effectiveTheme === 'dark' ? 'light' : 'dark';
		uiStore.setTheme(newTheme);
	}

	function exportData() {
		uiStore.notify('Export en cours...', 'info');
		// TODO: Implement export
		setTimeout(() => {
			uiStore.notify('Export terminé', 'success');
		}, 1000);
	}

	const settingsSections = [
		{
			title: 'Apparence',
			items: [
				{
					label: 'Thème',
					description: 'Choisir le thème de l\'interface',
					component: 'theme',
				},
			],
		},
		{
			title: 'API',
			items: [
				{
					label: 'Clé API',
					description: 'Clé pour accéder à l\'API protégée',
					component: 'apikey',
				},
			],
		},
		{
			title: 'Données',
			items: [
				{
					label: 'Actualisation auto',
					description: 'Intervalle de rafraîchissement des données',
					component: 'refresh',
				},
				{
					label: 'Exporter les données',
					description: 'Télécharger les données en CSV',
					component: 'export',
				},
			],
		},
	];
</script>

<div class="page-container">
	<header class="page-header">
		<div>
			<h1>Paramètres</h1>
			<p>Configurez l'interface selon vos préférences</p>
		</div>
	</header>

	<div class="settings-container">
		{#each settingsSections as section}
			<div class="settings-section">
				<h2 class="section-title">{section.title}</h2>
				<div class="settings-list">
					{#each section.items as item}
						<div class="setting-item">
							<div class="setting-info">
								<h3 class="setting-label">{item.label}</h3>
								<p class="setting-description">{item.description}</p>
							</div>
							<div class="setting-control">
								{#if item.component === 'theme'}
									<button class="theme-button" onclick={toggleTheme}>
										{$effectiveTheme === 'dark' ? '🌙 Sombre' : '☀️ Clair'}
									</button>
								{:else if item.component === 'apikey'}
									<div class="apikey-input-group">
										<input
											type={showApiKey ? 'text' : 'password'}
											placeholder="Clé API..."
											bind:value={apiKey}
											class="setting-input"
										/>
										<button
											class="icon-button"
											onclick={() => (showApiKey = !showApiKey)}
											aria-label="Toggle visibility"
										>
											{showApiKey ? '👁️' : '👁️‍🗨️'}
										</button>
										<button class="save-button" onclick={saveApiKey}>Enregistrer</button>
									</div>
								{:else if item.component === 'refresh'}
									<div class="refresh-options">
										{#each [10, 30, 60, 300] as interval}
											<button
												class="interval-button"
												class:active={refreshInterval === interval}
												onclick={() => (refreshInterval = interval)}
											>
												{interval < 60 ? interval + 's' : interval / 60 + 'min'}
											</button>
										{/each}
									</div>
									<button class="save-button" onclick={saveSettings}>Appliquer</button>
								{:else if item.component === 'export'}
									<button class="export-button" onclick={exportData}>
										<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
											<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" />
										</svg>
										Exporter CSV
									</button>
								{/if}
							</div>
						</div>
					{/each}
				</div>
			</div>
		{/each}
	</div>

	<!-- Info Section -->
	<div class="info-section">
		<h2 class="section-title">Informations</h2>
		<div class="info-card">
			<div class="info-item">
				<span class="info-label">Version</span>
				<span class="info-value">1.0.0</span>
			</div>
			<div class="info-item">
				<span class="info-label">Environnement</span>
				<span class="info-value">Production</span>
			</div>
			<div class="info-item">
				<span class="info-label">API</span>
				<span class="info-value">{apiUrl}</span>
			</div>
		</div>
	</div>
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

	.page-header h1 {
		margin: 0 0 0.25rem;
		font-size: 2rem;
		font-weight: 700;
		color: var(--text-primary);
	}

	.page-header p {
		margin: 0;
		color: var(--text-secondary);
	}

	.settings-container {
		display: flex;
		flex-direction: column;
		gap: 2rem;
		max-width: 800px;
	}

	.settings-section {
		background: var(--card-bg);
		border: 1px solid var(--border-color);
		border-radius: 12px;
		overflow: hidden;
	}

	.section-title {
		margin: 0;
		padding: 1.25rem;
		font-size: 0.875rem;
		font-weight: 600;
		color: var(--text-secondary);
		text-transform: uppercase;
		letter-spacing: 0.05em;
		border-bottom: 1px solid var(--border-color);
	}

	.settings-list {
		padding: 0;
	}

	.setting-item {
		display: flex;
		justify-content: space-between;
		align-items: center;
		padding: 1.25rem;
		border-bottom: 1px solid var(--border-color);
		gap: 1rem;
	}

	.setting-item:last-child {
		border-bottom: none;
	}

	.setting-info {
		flex: 1;
	}

	.setting-label {
		margin: 0 0 0.25rem;
		font-size: 1rem;
		font-weight: 500;
		color: var(--text-primary);
	}

	.setting-description {
		margin: 0;
		font-size: 0.875rem;
		color: var(--text-secondary);
	}

	.setting-control {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}

	.theme-button {
		padding: 0.625rem 1rem;
		background: var(--bg-primary);
		border: 1px solid var(--border-color);
		border-radius: 8px;
		cursor: pointer;
		font-weight: 500;
		color: var(--text-primary);
		transition: all 0.2s ease;
	}

	.theme-button:hover {
		background: var(--hover-bg);
		border-color: var(--accent-color);
	}

	.apikey-input-group {
		display: flex;
		gap: 0.5rem;
	}

	.setting-input {
		padding: 0.625rem 0.75rem;
		background: var(--bg-primary);
		border: 1px solid var(--border-color);
		border-radius: 8px;
		color: var(--text-primary);
		font-size: 0.9rem;
		min-width: 200px;
	}

	.setting-input:focus {
		outline: none;
		border-color: var(--accent-color);
		box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
	}

	.icon-button {
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 0.625rem;
		background: var(--bg-primary);
		border: 1px solid var(--border-color);
		border-radius: 8px;
		cursor: pointer;
		font-size: 1rem;
		transition: all 0.2s ease;
	}

	.icon-button:hover {
		background: var(--hover-bg);
	}

	.save-button {
		padding: 0.625rem 1rem;
		background: var(--accent-color);
		color: white;
		border: none;
		border-radius: 8px;
		cursor: pointer;
		font-weight: 500;
		font-size: 0.9rem;
		transition: background 0.2s ease;
	}

	.save-button:hover {
		background: var(--accent-hover);
	}

	.refresh-options {
		display: flex;
		gap: 0.5rem;
	}

	.interval-button {
		padding: 0.5rem 0.75rem;
		background: var(--bg-primary);
		border: 1px solid var(--border-color);
		border-radius: 6px;
		cursor: pointer;
		font-size: 0.875rem;
		color: var(--text-secondary);
		transition: all 0.2s ease;
	}

	.interval-button:hover {
		background: var(--hover-bg);
	}

	.interval-button.active {
		background: var(--accent-color);
		border-color: var(--accent-color);
		color: white;
	}

	.export-button {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.625rem 1rem;
		background: var(--bg-primary);
		border: 1px solid var(--border-color);
		border-radius: 8px;
		cursor: pointer;
		font-weight: 500;
		color: var(--text-primary);
		transition: all 0.2s ease;
	}

	.export-button:hover {
		background: var(--hover-bg);
		border-color: var(--accent-color);
	}

	/* Info Section */
	.info-section {
		margin-top: 2rem;
		max-width: 800px;
	}

	.info-card {
		background: var(--card-bg);
		border: 1px solid var(--border-color);
		border-radius: 12px;
		padding: 1.25rem;
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
		gap: 1rem;
	}

	.info-item {
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
	}

	.info-label {
		font-size: 0.75rem;
		color: var(--text-secondary);
	}

	.info-value {
		font-size: 0.9rem;
		font-weight: 500;
		color: var(--text-primary);
	}

	@media (max-width: 640px) {
		.setting-item {
			flex-direction: column;
			align-items: flex-start;
		}

		.setting-control {
			width: 100%;
			justify-content: flex-end;
		}

		.apikey-input-group {
			width: 100%;
		}

		.setting-input {
			flex: 1;
		}
	}
</style>
