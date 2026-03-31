/**
 * Format utilities
 */

/**
 * Format a number as currency
 */
export function formatPrice(price: number): string {
	return new Intl.NumberFormat('fr-FR', {
		style: 'currency',
		currency: 'EUR',
		minimumFractionDigits: 2,
		maximumFractionDigits: 2,
	}).format(price);
}

/**
 * Format a number with thousand separators
 */
export function formatNumber(num: number): string {
	return new Intl.NumberFormat('fr-FR').format(num);
}

/**
 * Format a timestamp as relative time
 */
export function formatRelativeTime(timestamp: number): string {
	const now = Date.now();
	const diff = now - timestamp;

	const seconds = Math.floor(diff / 1000);
	const minutes = Math.floor(seconds / 60);
	const hours = Math.floor(minutes / 60);
	const days = Math.floor(hours / 24);

	if (seconds < 60) return "À l'instant";
	if (minutes < 60) return `Il y a ${minutes} min`;
	if (hours < 24) return `Il y a ${hours}h`;
	if (days < 7) return `Il y a ${days}j`;

	const date = new Date(timestamp);
	return date.toLocaleDateString('fr-FR');
}

/**
 * Format a timestamp as date and time
 */
export function formatDateTime(timestamp: number): string {
	return new Date(timestamp).toLocaleString('fr-FR', {
		day: '2-digit',
		month: '2-digit',
		year: 'numeric',
		hour: '2-digit',
		minute: '2-digit',
	});
}

/**
 * Get color class based on value change
 */
export function getChangeColor(value: number): string {
	if (value > 0) return 'positive';
	if (value < 0) return 'negative';
	return 'neutral';
}

/**
 * Truncate text with ellipsis
 */
export function truncate(text: string, maxLength: number): string {
	if (text.length <= maxLength) return text;
	return text.substring(0, maxLength - 3) + '...';
}

/**
 * Format percentage
 */
export function formatPercent(value: number, decimals = 2): string {
	const formatted = value.toFixed(decimals);
	return `${value > 0 ? '+' : ''}${formatted}%`;
}
