import { readdir, readFile } from 'node:fs/promises';

// Read-only seed inventory. Semantic quality and the running database need separate review.
const directory = new URL('../backend/src/main/resources/data/', import.meta.url);
const files = (await readdir(directory)).filter(name => /^curriculum-.*\.json$/.test(name)).sort();
if (!files.length) throw new Error('Nem található curriculum-*.json seed.');
const issues = [];
const review = [];
const rows = [];
const validLanguage = value => {
    if (typeof value !== 'string' || !value.trim()) return false;
    try { new Intl.Locale(value); return true; } catch { return false; }
};
const cell = value => String(value).replaceAll('|', '\\|').replaceAll('\n', ' ');

for (const file of files) {
    const topics = JSON.parse(await readFile(new URL(file, directory), 'utf8'));
    const counts = { exercises: 0, images: 0, textImages: 0, hints: 0, annotatedHints: 0, mixedQuestions: 0 };
    const initialIssues = issues.length;
    for (const [topicIndex, topic] of topics.entries()) {
        for (const [lessonIndex, lesson] of topic.lessons.entries()) {
            let lessonImages = 0;
            let lessonHints = 0;
            for (const [exerciseIndex, exercise] of lesson.exercises.entries()) {
                counts.exercises++;
                const location = `${file} / ${topicIndex + 1}.${lessonIndex + 1}.${exerciseIndex + 1} (${lesson.title})`;
                const content = exercise.content || {};
                const issue = text => issues.push(`${location}: ${text}`);
                for (const key of ['questionLang', 'answerLang']) {
                    if (!validLanguage(content[key])) issue(`hiányzó vagy Intl.Locale által nem értelmezhető ${key}`);
                }
                if (content.questionParts !== undefined) {
                    const parts = content.questionParts;
                    if (!Array.isArray(parts) || !parts.length
                        || !parts.every(part => typeof part?.text === 'string' && validLanguage(part?.lang))
                        || parts.map(part => part.text).join('') !== content.question) {
                        issue('hibás questionParts vagy a question szövegétől eltérő összefűzés');
                    } else counts.mixedQuestions++;
                }
                const imageUrl = exercise.imageUrl || content.imageUrl;
                if (exercise.type === 'IMAGE_CHOICE' && !imageUrl) issue('képes feladat kép nélkül');
                if (imageUrl) {
                    counts.images++;
                    if (typeof content.imageAlt !== 'string' || !content.imageAlt.trim()) issue('hiányzó imageAlt');
                    if (!validLanguage(content.imageAltLang)) issue('hiányzó/érvénytelen imageAltLang');
                    if (!['informative', 'visual-assessment'].includes(content.imagePurpose)) issue('hiányzó/érvénytelen imagePurpose');
                    try {
                        const image = new URL(imageUrl, 'https://seed.invalid');
                        if (image.hostname === 'placehold.co' && image.searchParams.has('text')) {
                            counts.textImages++; lessonImages++;
                        }
                    } catch { issue('nem értelmezhető kép URL'); }
                }
                if (typeof content.hint === 'string' && content.hint.trim()) {
                    counts.hints++; lessonHints++;
                    const parts = content.hintParts;
                    if (content.hintDirection !== 'hu-to-target' || !Array.isArray(parts) || !parts.length
                        || !parts.every(part => typeof part?.text === 'string' && validLanguage(part?.lang))
                        || parts.map(part => part.text).join('') !== content.hint) {
                        issue('hiányzó/eltérő hintDirection vagy hintParts');
                    } else counts.annotatedHints++;
                }
            }
            if (lessonImages || lessonHints) review.push({ file, lesson: lesson.title, images: lessonImages, hints: lessonHints });
        }
    }
    rows.push({ file, ...counts, issues: issues.length - initialIssues });
}

console.log('# Seed tananyag akadálymentességi leltár\n');
console.log('Csak olvasás: seed JSON, nem az élő adatbázis; nincs hálózati kérés vagy adatjavítás.\n');
console.log('| Fájl | Feladat | Kép | Szöveges placeholder | Tipp | Nyelvjelölt tipp | questionParts | Szerkezeti hiba |');
console.log('|---|---:|---:|---:|---:|---:|---:|---:|');
for (const row of rows) console.log(`| ${row.file} | ${row.exercises} | ${row.images} | ${row.textImages} | ${row.hints} | ${row.annotatedHints} | ${row.mixedQuestions} | ${row.issues} |`);
console.log('\n## Szerzői felülvizsgálat\n');
console.log('A képek tudatosan ideiglenes placeholderek (2026-10-19-i szerzői döntés); végleges képfelismerési tartalomhoz lecserélendők. A tippek egységes magyar → tanult nyelv irányúak, explicit nyelvi részekkel. A számláló nem automatikus WCAG-hibaminősítés.\n');
console.log('| Fájl | Lecke | Szöveges placeholder | Nem üres tipp |');
console.log('|---|---|---:|---:|');
for (const row of review) console.log(`| ${row.file} | ${cell(row.lesson)} | ${row.images} | ${row.hints} |`);
console.log('\n## Szerkezeti eredmény\n');
console.log(issues.length ? issues.map(issue => `- ${issue}`).join('\n') : 'Nincs talált szerkezeti hiba a vizsgált mezőkben.');
console.log('\nAz Intl.Locale ellenőrzés nem teljes BCP 47 regiszter- vagy nyelvhelyesség-vizsgálat. Az alt jelentését, a képfelismerési kivétel indokát és a fordítások helyességét ez a leltár nem igazolja.');
if (issues.length) process.exitCode = 1;
