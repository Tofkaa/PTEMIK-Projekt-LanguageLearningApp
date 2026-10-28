import { expect, it } from 'vitest';
import { rateLimitMessage } from './rateLimitMessage';
it.each([['15',15],['1.2',2],[undefined,60],['garbage',60],['-10',60],['999999999',60]])('bounds Retry-After %s into plain spoken seconds', (value, expected) => {
    expect(rateLimitMessage({ response: { headers: { 'retry-after': value } } })).toBe(`Túl sok kérés. Próbáld újra ${expected} másodperc múlva.`);
});
