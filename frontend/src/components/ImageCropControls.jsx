import { useId } from 'react';
import { Button, Form } from 'react-bootstrap';

/** Single-click movement also works with native button keyboard activation. */
export default function ImageCropControls({ crop, onCropChange, zoom, onZoomChange, bounds = { x: 0, y: 0 }, disabled = false }) {
    const id = useId();
    const move = (x, y) => onCropChange({
        x: Math.max(-bounds.x, Math.min(bounds.x, crop.x + x)),
        y: Math.max(-bounds.y, Math.min(bounds.y, crop.y + y)),
    });
    return <fieldset disabled={disabled}>
        <legend className="fs-6 fw-bold">Kép elhelyezése</legend>
        <p id={`${id}-help`} className="small text-body-secondary">A képet húzás nélkül is elhelyezheted az iránygombokkal. A nagyítást a csúszkával állíthatod.</p>
        <div role="group" aria-label="Kép mozgatása" aria-describedby={`${id}-help`} className="d-flex flex-wrap gap-2 mb-3">
            <Button variant="outline-info" onClick={() => move(-10, 0)} aria-label="Kép mozgatása balra"><span aria-hidden="true">←</span> Balra</Button>
            <Button variant="outline-info" onClick={() => move(10, 0)} aria-label="Kép mozgatása jobbra"><span aria-hidden="true">→</span> Jobbra</Button>
            <Button variant="outline-info" onClick={() => move(0, -10)} aria-label="Kép mozgatása felfelé"><span aria-hidden="true">↑</span> Felfelé</Button>
            <Button variant="outline-info" onClick={() => move(0, 10)} aria-label="Kép mozgatása lefelé"><span aria-hidden="true">↓</span> Lefelé</Button>
            <Button variant="outline-secondary" onClick={() => onCropChange({ x: 0, y: 0 })}>Kép középre</Button>
        </div>
        <Form.Group controlId={`${id}-zoom`}>
            <div className="d-flex justify-content-between mb-1">
                <Form.Label className="small text-body-secondary mb-0">Nagyítás (Zoom)</Form.Label>
                <span aria-hidden="true" className="small app-accent-text fw-bold">{Math.round(zoom * 100)}%</span>
            </div>
            <Form.Range min={1} max={3} step={0.05} value={zoom} aria-valuetext={`${Math.round(zoom * 100)} százalék`}
                onChange={event => onZoomChange(Number(event.target.value))} />
        </Form.Group>
    </fieldset>;
}
