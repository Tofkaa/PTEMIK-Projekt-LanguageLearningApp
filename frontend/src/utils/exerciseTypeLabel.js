const labels = {
    MULTIPLE_CHOICE: 'Feleletválasztás',
    WORD_BANK: 'Szóbank',
    TRANSLATION: 'Fordítás',
    IMAGE_CHOICE: 'Képes választás',
};

export const exerciseTypeLabel = type => labels[type] || 'Ismeretlen feladattípus';
