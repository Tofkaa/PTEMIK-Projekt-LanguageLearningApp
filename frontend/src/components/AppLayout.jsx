import { useLayoutEffect, useRef } from 'react';
import { matchPath, useLocation } from 'react-router-dom';
import { wrapModalFocus } from '../utils/modalFocus';

const pageTitles = [
    ['/login', 'Bejelentkezés'],
    ['/register', 'Regisztráció'],
    ['/forgot-password', 'Elfelejtett jelszó'],
    ['/reset-password', 'Új jelszó beállítása'],
    ['/account-deletion', 'Fióktörlés áttekintése'],
    ['/terms', 'Felhasználási feltételek'],
    ['/privacy', 'Adatkezelési tájékoztató'],
    ['/dashboard', 'Áttekintés'],
    ['/settings', 'Beállítások'],
    ['/classrooms', 'Osztálytermeim'],
    ['/classrooms/:id', 'Osztályterem'],
    ['/assignment/:id/start', 'Feladat indítása'],
    ['/assignment/session/:sessionId/play', 'Feladatmegoldás'],
    ['/assignment/:id/submissions', 'Beadások értékelése'],
    ['/hub', 'Szótár'],
    ['/vocabulary/practice', 'Szókincsgyakorlás'],
    ['/vocabulary/flashcards', 'Szókártyás gyakorlás'],
    ['/lesson/:id', 'Lecke'],
    ['/profile', 'Profilom'],
    ['/oauth2/redirect', 'Bejelentkezés folyamatban'],
    ['/friends', 'Közösség'],
    ['/verify', 'E-mail-cím megerősítése'],
    ['/admin', 'Adminisztráció'],
];

/** Keep route changes discoverable without stealing focus on in-page updates. */
export default function AppLayout({ navigation, children, footer }) {
    const { pathname } = useLocation();
    const mainRef = useRef(null);
    const shellRef = useRef(null);
    const previousPath = useRef(pathname);

    useLayoutEffect(() => {
        const shell = shellRef.current;
        // Bootstrap traps keyboard focus; also exclude its background from virtual-cursor reading.
        const synchronizeModalBackground = () => {
            const hasModal = [...document.querySelectorAll('[role="dialog"][aria-modal="true"]')]
                .some(dialog => !shell.contains(dialog));
            if (hasModal) shell.setAttribute('aria-hidden', 'true');
            else shell.removeAttribute('aria-hidden');
        };
        const observer = new MutationObserver(synchronizeModalBackground);
        const handleModalTab = event => {
            const dialog = [...document.querySelectorAll('[role="dialog"][aria-modal="true"]')]
                .filter(element => !element.closest('[aria-hidden="true"]') && element.getClientRects().length > 0).at(-1);
            if (dialog) wrapModalFocus(event, dialog);
        };
        document.addEventListener('keydown', handleModalTab);
        observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['role', 'aria-modal'] });
        synchronizeModalBackground();
        return () => {
            observer.disconnect();
            document.removeEventListener('keydown', handleModalTab);
            shell.removeAttribute('aria-hidden');
        };
    }, []);

    useLayoutEffect(() => {
        const title = pageTitles.find(([path]) => matchPath(path, pathname))?.[1]
            ?? (pathname === '/' ? 'Kezdőlap' : 'Az oldal nem található');
        document.title = `${title} | LanguageApp`;
        if (previousPath.current !== pathname) {
            mainRef.current?.focus();
            previousPath.current = pathname;
        }
    }, [pathname]);

    return <div ref={shellRef}>
        <a className="skip-link" href="#main-content" onClick={event => {
            // Moving focus must not overwrite a recovery/approval secret in the URL fragment.
            event.preventDefault();
            mainRef.current?.focus();
        }}>
            Ugrás a tartalomra
        </a>
        {navigation}
        <main id="main-content" ref={mainRef} tabIndex={-1} aria-label="Fő tartalom">
            {children}
        </main>
        {footer}
    </div>;
}
