import React, { useState, useEffect } from 'react';
import * as JoyrideModule from 'react-joyride';
import { useAuth } from '../context/AuthContext.jsx'; 

const Joyride = typeof JoyrideModule.default === 'function' ? JoyrideModule.default : JoyrideModule.Joyride;

// --- SAJÁT, BOOTSTRAP-ALAPÚ BUBORÉK KOMPONENS ---
const CustomTooltip = ({
    index,
    step,
    backProps,
    primaryProps,
    skipProps,
    isLastStep,
    size,
}) => {
    return (
        <div className="bg-white rounded-4 shadow-lg p-4 position-relative" style={{ maxWidth: '400px', border: '1px solid #dee2e6', zIndex: 10550 }}>
            {/* Fejléc és Tartalom */}
            <div className="mb-3">
                {step.title && <div className="mb-2">{step.title}</div>}
                <div className="text-dark">{step.content}</div>
            </div>
            
            {/* Lábléc (Footer) a gombokkal és a számlálóval */}
            <div className="d-flex justify-content-between align-items-center mt-4 pt-3 border-top border-light">
                
                {/* Bal oldal: Kihagyás gomb és Lépésszámláló */}
                <div className="d-flex align-items-center gap-3">
                    {!isLastStep && (
                        <button {...skipProps} className="btn btn-link text-secondary p-0 text-decoration-none fw-bold small">
                            Kihagyás
                        </button>
                    )}
                    <span className="badge bg-light text-secondary border">
                        {index + 1} / {size}
                    </span>
                </div>

                {/* Jobb oldal: Vissza és Tovább gombok */}
                <div className="d-flex gap-2">
                    {index > 0 && (
                        <button {...backProps} className="btn btn-sm btn-outline-info fw-bold px-3">
                            Vissza
                        </button>
                    )}
                    <button {...primaryProps} className="btn btn-sm btn-info fw-bold text-dark px-3">
                        {isLastStep ? 'Készen vagyok!' : 'Tovább'}
                    </button>
                </div>
            </div>
        </div>
    );
};


const TutorialTour = () => {
    const { user } = useAuth(); 
    const [run, setRun] = useState(false);

    const [steps] = useState([
        {
            target: 'body',
            placement: 'center',
            title: <strong className="fs-5 text-info">Üdvözlünk a LanguageApp-ban! 👋</strong>,
            content: "Nézzük végig a legfontosabb funkciókat, hogy a lehető leggyorsabban elkezdhesd a nyelvtanulást!",
            disableBeacon: true,
        },
        {
            target: '.tour-navbar-classrooms',
            title: <strong className="text-info">🏫 Osztálytermek</strong>,
            content: "Itt csatlakozhatsz tanári osztálytermekhez, ahol egyedi házi feladatokat és teszteket kaphatsz, amelyeket a tanárod tud értékelni.",
        },
        {
            target: '.tour-navbar-community',
            title: <strong className="text-info">🌐 Közösség</strong>,
            content: "Vedd fel a barátaidat a profiljukon található Barátkód segítségével, és versenyezzetek egymással!",
        },
        {
            target: '.tour-dictionary-hub',
            title: <strong className="text-info">🎯 Szótár HUB</strong>,
            content: "Olvasás közben bármelyik idegen szóra rákattinthatsz a leckékben! A lefordított és elmentett szavaidat itt találod, és napi kvízekkel gyakorolhatod őket.",
        },
        {
            target: '.tour-hero-widget',
            title: <strong className="text-info">🚀 Intelligens ajánló</strong>,
            content: "Nem kell keresgélned! A rendszer a haladásod és az adaptív algoritmus alapján mindig automatikusan felkínálja neked a következő legideálisabb leckét.",
        },
        {
            target: '.tour-learning-path',
            placement: 'bottom',
            title: <strong className="text-info">📚 Tanulási Útvonal</strong>,
            content: "Persze te magad is válogathatsz: itt találod az összes elérhető leckét témakörökre és nehézségre bontva.",
        },
        {
            target: '.tour-performance',
            placement: 'left-start', 
            title: <strong className="text-info">📊 Teljesítmény</strong>,
            content: "Kövesd nyomon a napi sorozatodat (streak), a szintlépéshez szükséges XP-det és a tanfolyam előrehaladását.",
        },
        {
            target: '.tour-leaderboard',
            placement: 'left-start', 
            title: <strong className="text-info">🏆 Ranglista</strong>,
            content: "Nézd meg, hol állsz a globális vagy a barátaid ranglistáján, legyen szó XP-ről vagy a Napi sorozatról!",
        },
        {
            target: '.tour-profile-menu',
            title: <strong className="text-info">⚙ Profil és Beállítások</strong>,
            content: <span>Kattints ide a <strong>Profilodhoz</strong>, ahol láthatod a kitüntetéseidet és a Barátkódodat! A <strong>Beállításokban</strong> pedig személyre szabhatod a fiókodat és a tanulási nehézséget.</span>,
        }
    ]);

    useEffect(() => {
        if (!user) return; 
        
        const identifier = user.userId || user.id || user.email;
        const tutorialKey = `hasSeenTutorial_${identifier}`;
        const hasSeenTutorial = localStorage.getItem(tutorialKey);
        
        if (!hasSeenTutorial) {
            localStorage.setItem(tutorialKey, 'true');
            setTimeout(() => setRun(true), 1000);
        }
    }, [user]);

    const handleJoyrideCallback = (data) => {
        const { status, action } = data;
        
        if (['finished', 'skipped'].includes(status) || action === 'close') {
            setRun(false);
        }
    };

return (
        <Joyride
            steps={steps}
            run={run}
            continuous={true}
            scrollOffset={100}
            callback={handleJoyrideCallback}
            tooltipComponent={CustomTooltip}
            floaterProps={{
                options: {
                    modifiers: [
                        {
                            name: 'computeStyles',
                            options: {
                                adaptive: false,
                            },
                        },
                    ],
                },
            }}
            styles={{
                options: {
                    zIndex: 10550,
                    overlayColor: 'rgba(0, 0, 0, 0.75)',
                }
            }}
        />
    );
};

export default TutorialTour;