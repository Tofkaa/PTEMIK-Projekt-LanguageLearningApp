import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Joyride } from 'react-joyride';
import { Button } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext.jsx'; 
import TutorialTooltip from './TutorialTooltip';

const TutorialTour = () => {
    const { user } = useAuth(); 
    const [run, setRun] = useState(false);
    const returnFocus = useRef(null);
    const running = useRef(false);
    const [wideNavigation, setWideNavigation] = useState(() => window.matchMedia?.('(min-width: 992px)').matches ?? true);
    useEffect(() => {
        const media = window.matchMedia?.('(min-width: 992px)');
        if (!media) return;
        const update = () => setWideNavigation(media.matches);
        update();
        media.addEventListener('change', update);
        return () => media.removeEventListener('change', update);
    }, []);
    const identifier = user?.userId || user?.id || user?.email;
    const startTour = useCallback(() => {
        if (running.current) return;
        running.current = true;
        returnFocus.current = document.activeElement;
        setRun(true);
    }, []);

    useEffect(() => {
        if (!run) return;
        return () => {
            const target = returnFocus.current?.isConnected && returnFocus.current !== document.body
                ? returnFocus.current : document.getElementById('main-content');
            // Restore the original trigger after Joyride cleans up the final step's focus trap.
            requestAnimationFrame(() => { if (target?.isConnected) target.focus({ preventScroll: true }); });
        };
    }, [run]);

    const [baseSteps] = useState([
        {
            target: 'body',
            placement: 'center',
            title: <>Üdvözlünk a LanguageApp-ban! <span aria-hidden="true">👋</span></>,
            content: "Nézzük végig a legfontosabb funkciókat, hogy a lehető leggyorsabban elkezdhesd a nyelvtanulást!",
        },
        {
            target: '.tour-navbar-classrooms',
            title: <><span aria-hidden="true">🏫 </span>Osztálytermek</>,
            content: "Itt csatlakozhatsz tanári osztálytermekhez, ahol egyedi házi feladatokat és teszteket kaphatsz, amelyeket a tanárod tud értékelni.",
        },
        {
            target: '.tour-navbar-community',
            title: <><span aria-hidden="true">🌐 </span>Közösség</>,
            content: "Vedd fel a barátaidat a profiljukon található Barátkód segítségével, és versenyezzetek egymással!",
        },
        {
            target: '.tour-dictionary-hub',
            title: <><span aria-hidden="true">🎯 </span>Szótár HUB</>,
            content: 'A leckék idegen szavait kattintással vagy Tab és Enter/Space billentyűvel is lefordíthatod. Az elmentett szavaidat itt találod, és napi kvízekkel gyakorolhatod őket.',
        },
        {
            target: '.tour-hero-widget',
            title: <><span aria-hidden="true">🚀 </span>Intelligens ajánló</>,
            content: "Nem kell keresgélned! A rendszer a haladásod és az adaptív algoritmus alapján mindig automatikusan felkínálja neked a következő legideálisabb leckét.",
        },
        {
            target: '.tour-learning-path',
            placement: 'bottom',
            title: <><span aria-hidden="true">📚 </span>Tanulási Útvonal</>,
            content: "Persze te magad is válogathatsz: itt találod az összes elérhető leckét témakörökre és nehézségre bontva.",
        },
        {
            target: '.tour-performance',
            placement: 'left-start', 
            title: <><span aria-hidden="true">📊 </span>Teljesítmény</>,
            content: "Kövesd nyomon a napi sorozatodat (streak), a szintlépéshez szükséges XP-det és a tanfolyam előrehaladását.",
        },
        {
            target: '.tour-leaderboard',
            placement: 'left-start', 
            title: <><span aria-hidden="true">🏆 </span>Ranglista</>,
            content: "Nézd meg, hol állsz a globális vagy a barátaid ranglistáján, legyen szó XP-ről vagy a Napi sorozatról!",
        },
        {
            target: '.tour-profile-menu',
            title: <><span aria-hidden="true">⚙ </span>Profil és Beállítások</>,
            content: <span>Kattints ide a <strong>Profilodhoz</strong>, ahol láthatod a kitüntetéseidet és a Barátkódodat! A <strong>Beállításokban</strong> pedig személyre szabhatod a fiókodat és a tanulási nehézséget.</span>,
        }
    ]);

    const steps = useMemo(() => baseSteps.map(step => !wideNavigation && [
        '.tour-navbar-classrooms', '.tour-navbar-community', '.tour-dictionary-hub', '.tour-profile-menu',
    ].includes(step.target) ? {
        ...step,
        target: 'body',
        placement: 'center',
        content: <><p>Keskeny nézetben ezt a Navigációs menü gombbal megnyitható menüben találod.</p><div>{step.content}</div></>,
    } : step), [baseSteps, wideNavigation]);

    useEffect(() => {
        if (!identifier) return;
        const tutorialKey = `hasSeenTutorial_${identifier}`;
        const hasSeenTutorial = localStorage.getItem(tutorialKey);
        
        if (!hasSeenTutorial) {
            const timer = setTimeout(() => { if (!localStorage.getItem(tutorialKey)) startTour(); }, 1000);
            return () => clearTimeout(timer);
        }
    }, [identifier, startTour]);

    const handleJoyrideCallback = (data) => {
        const { status, action } = data;
        
        if (['finished', 'skipped'].includes(status) || action === 'close') {
            running.current = false;
            if (identifier) localStorage.setItem(`hasSeenTutorial_${identifier}`, 'true');
            setRun(false);
        }
    };

return <>
        <div className="container d-flex justify-content-end pt-3">
            <Button variant="outline-info" size="sm" onClick={startTour} aria-disabled={run}>Az oldal bemutatása</Button>
        </div>
        <Joyride
            steps={steps}
            run={run}
            continuous={true}
            onEvent={handleJoyrideCallback}
            tooltipComponent={TutorialTooltip}
            locale={{ back: 'Vissza', close: 'Bezárás', last: 'Készen vagyok!', next: 'Tovább', skip: 'Kihagyás', open: 'Bemutató megnyitása' }}
            options={{ skipBeacon: true, disableFocusTrap: true, blockTargetInteraction: true, dismissKeyAction: 'close', scrollOffset: 100,
                scrollDuration: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 0 : 300,
                zIndex: 10550, overlayColor: 'rgba(0, 0, 0, 0.75)', arrowColor: 'var(--bs-body-bg)' }}
        />
    </>;
};

export default TutorialTour;
