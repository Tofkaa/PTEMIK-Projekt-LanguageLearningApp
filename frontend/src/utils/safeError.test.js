import { expect, it } from 'vitest';
import { safeError } from './safeError';
it('excludes credentials, URLs, bodies, messages and stacks', () => {
    const secret = 'synthetic-secret';
    const result = safeError({ code: 'ERR_BAD_RESPONSE', response: { status: 401, data: secret }, message: secret,
        stack: secret, config: { headers: { Authorization: secret }, _authToken: secret, url: secret, data: secret } });
    expect(result).toEqual({ status: 401, code: 'ERR_BAD_RESPONSE' }); expect(JSON.stringify(result)).not.toContain(secret);
    expect(safeError({ code: secret, response: { status: secret } })).toEqual({ status: null, code: 'ERROR' });
});
