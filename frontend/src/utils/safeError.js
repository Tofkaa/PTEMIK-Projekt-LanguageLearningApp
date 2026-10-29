/** Console diagnostics must not include Axios configs, headers, bodies, URLs or credential-bearing messages. */
export function safeError(error) {
    const status = error?.response?.status;
    const allowed = new Set(['ERR_CANCELED', 'ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT', 'ERR_BAD_REQUEST', 'ERR_BAD_RESPONSE']);
    const headers = error?.response?.headers;
    const header = typeof headers?.get === 'function' ? headers.get('x-request-id') : headers?.['x-request-id'];
    const requestId = typeof header === 'string' && /^(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/.test(header) ? header : null;
    return { ...(requestId ? { requestId } : {}), status: Number.isInteger(status) && status >= 100 && status <= 599 ? status : null,
        code: allowed.has(error?.code) ? error.code : 'ERROR' };
}
