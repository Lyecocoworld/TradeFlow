/**
 * Debounce utility - Delays function execution until after wait milliseconds
 */
export function debounce<T extends (...args: any[]) => any>(
	fn: T,
	wait: number
): (...args: Parameters<T>) => void {
	let timeout: ReturnType<typeof setTimeout> | null = null;

	return function executedFunction(...args: Parameters<T>) {
		const later = () => {
			timeout = null;
			fn(...args);
		};

		if (timeout) {
			clearTimeout(timeout);
		}
		timeout = setTimeout(later, wait);
	};
}

/**
 * Throttle utility - Limits function execution to once per wait milliseconds
 */
export function throttle<T extends (...args: any[]) => any>(
	fn: T,
	wait: number
): (...args: Parameters<T>) => void {
	let inThrottle = false;

	return function executedFunction(...args: Parameters<T>) {
		if (!inThrottle) {
			fn(...args);
			inThrottle = true;
			setTimeout(() => (inThrottle = false), wait);
		}
	};
}
