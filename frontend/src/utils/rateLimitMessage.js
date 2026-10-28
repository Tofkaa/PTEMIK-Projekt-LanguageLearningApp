/** Only a bounded numeric Retry-After becomes spoken UI text; no automatic replay. */
export function rateLimitMessage(error) {
    const delay = Number(error?.response?.headers?.['retry-after']);
    const seconds = Number.isFinite(delay) && delay > 0 && delay <= 86400 ? Math.ceil(delay) : 60;
    return `Túl sok kérés. Próbáld újra ${seconds} másodperc múlva.`;
}
