const characterNames = {
    A: 'a', B: 'bé', C: 'cé', D: 'dé', E: 'e', F: 'ef', G: 'gé', H: 'há', I: 'i',
    J: 'jé', K: 'ká', L: 'el', M: 'em', N: 'en', O: 'o', P: 'pé', Q: 'kú', R: 'er',
    S: 'es', T: 'té', U: 'u', V: 'vé', W: 'dupla vé', X: 'iksz', Y: 'ipszilon', Z: 'zé',
    0: 'nulla', 1: 'egy', 2: 'kettő', 3: 'három', 4: 'négy', 5: 'öt',
    6: 'hat', 7: 'hét', 8: 'nyolc', 9: 'kilenc', '-': 'kötőjel',
};

/** Spell identifiers without relying on a screen reader's number/Roman-numeral heuristics. */
export default function AccessibleCode({ code }) {
    if (!code) return null;
    const spoken = [...String(code)].map(character => characterNames[character.toUpperCase()] || character).join(', ');
    return <><span aria-hidden="true">{code}</span><span className="visually-hidden" lang="hu">Karakterenként: {spoken}</span></>;
}
