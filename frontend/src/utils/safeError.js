/** Console diagnostics must not include Axios configs, headers, bodies, URLs or credential-bearing messages. */
export function safeError(error) {
    const status = error?.response?.status;
    const allowed = new Set(['ERR_CANCELED', 'ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT', 'ERR_BAD_REQUEST', 'ERR_BAD_RESPONSE']);
    return { status: Number.isInteger(status) && status >= 100 && status <= 599 ? status : null,
        code: allowed.has(error?.code) ? error.code : 'ERROR' };
}
