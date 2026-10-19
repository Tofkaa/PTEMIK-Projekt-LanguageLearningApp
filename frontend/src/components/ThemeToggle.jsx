import { useId } from 'react';
import { Form } from 'react-bootstrap';
import { useTheme } from '../context/ThemeContext';

const ThemeToggle = () => {
    const { themePreference, setThemePreference } = useTheme();
    const id = useId();

    return (
        <div className="theme-picker">
            <Form.Label htmlFor={id} className="visually-hidden">Megjelenési téma</Form.Label>
            <Form.Select id={id} size="sm" value={themePreference}
                onChange={event => setThemePreference(event.target.value)}>
                <option value="system">Téma: Rendszer</option>
                <option value="light">Téma: Világos</option>
                <option value="dark">Téma: Sötét</option>
            </Form.Select>
        </div>
    );
};

export default ThemeToggle;
