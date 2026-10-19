/** Keep the compact clock visual while exposing explicit units to reading navigation. */
export default function DurationText({ seconds, label = '', overtime = false, id }) {
    const total = Math.max(0, Math.floor(Math.abs(seconds)));
    const minutes = Math.floor(total / 60);
    const remainder = total % 60;
    const clock = `${String(minutes).padStart(2, '0')}:${String(remainder).padStart(2, '0')}`;
    return <>
        <span aria-hidden="true">{overtime ? '+' : ''}{clock}</span>
        <span id={id} lang="hu" className="visually-hidden">{label ? `${label}: ` : ''}{minutes} perc {remainder} másodperc</span>
    </>;
}
