import { createContext, useContext } from 'react';
import { useAuth } from './AuthContext';
const VocabularyCourseContext = createContext(null);
export function VocabularyCourseProvider({ courseId, courseCode, children }) {
    return <VocabularyCourseContext.Provider value={courseId ? { courseId, courseCode, revision: 0 } : null}>
        {children}
    </VocabularyCourseContext.Provider>;
}
// eslint-disable-next-line react-refresh/only-export-components
export function useVocabularyCourse() {
    const fixed = useContext(VocabularyCourseContext);
    const { user } = useAuth() || {};
    return fixed || { courseId: user?.activeCourseId, courseCode: user?.activeCourseCode, revision: user?.activeCourseRevision };
}
