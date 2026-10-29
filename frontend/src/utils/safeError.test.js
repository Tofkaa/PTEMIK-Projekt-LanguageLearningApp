import { expect, it } from 'vitest';
import { safeError } from './safeError';
it('excludes credentials, URLs, bodies, messages and stacks', () => {
    const secret = 'synthetic-secret';
    const result = safeError({ code: 'ERR_BAD_RESPONSE', response: { status: 401, data: secret }, message: secret,
        stack: secret, config: { headers: { Authorization: secret }, _authToken: secret, url: secret, data: secret } });
    expect(result).toEqual({ status: 401, code: 'ERR_BAD_RESPONSE' }); expect(JSON.stringify(result)).not.toContain(secret);
    expect(safeError({ code: secret, response: { status: secret } })).toEqual({ status: null, code: 'ERROR' });
});

it('keeps only a canonical request UUID from response headers', () => {
    const id='12345678-1234-4234-8234-123456789abc';
    expect(safeError({response:{status:500,headers:{'x-request-id':id}}})).toMatchObject({requestId:id,status:500});
    expect(safeError({response:{headers:{get:()=>id}}})).toHaveProperty('requestId',id);
    expect(safeError({response:{headers:{'x-request-id':'secret\nAuthorization: credential'}}})).not.toHaveProperty('requestId');
});

it('also correlates edge-only failures without accepting arbitrary header values', () => {
    const id='123456789abcdef0123456789abcdef0';
    expect(safeError({response:{status:502,headers:{'x-request-id':id,get:'not-a-function'}}})).toHaveProperty('requestId',id);
});
