/** Wrap Tab at a modal's boundaries while leaving native navigation inside it intact. */
export function wrapModalFocus(event, dialog) {
    if (event.key !== 'Tab' || event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return;
    const candidates = [...dialog.querySelectorAll(
        'a[href], area[href], button, input, select, textarea, summary, [tabindex], [contenteditable="true"]'
    )].filter(element => element.tabIndex >= 0 && !element.matches(':disabled')
        && !element.closest('[hidden], [inert], [aria-hidden="true"]')
        && element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden');
    const tabbable = candidates.filter(element => {
        if (!element.matches('input[type="radio"]') || !element.name) return true;
        const group = candidates.filter(other => other.matches('input[type="radio"]')
            && other.name === element.name && other.form === element.form);
        return element === (group.find(other => other.checked) || group[0]);
    });
    const active = dialog.ownerDocument.activeElement;
    const first = tabbable[0];
    const last = tabbable.at(-1);
    const entering = active === dialog || !dialog.contains(active);
    if (!first) {
        event.preventDefault();
        dialog.focus();
    } else if (event.shiftKey && (active === first || entering)) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && (active === last || entering)) {
        event.preventDefault();
        first.focus();
    }
}
