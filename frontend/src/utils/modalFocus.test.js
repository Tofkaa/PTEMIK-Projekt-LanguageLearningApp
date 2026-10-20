import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { wrapModalFocus } from './modalFocus';

let dialog;
beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getClientRects').mockReturnValue([{}]);
    dialog = document.createElement('div');
    dialog.tabIndex = -1;
    document.body.append(dialog);
});
afterEach(() => { dialog.remove(); vi.restoreAllMocks(); });
function tab(shiftKey = false) {
    const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, cancelable: true });
    wrapModalFocus(event, dialog);
    return event;
}
it('keeps an empty or single-control dialog reachable', () => {
    dialog.focus();
    expect(tab().defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(dialog);
    dialog.innerHTML = '<button>Only</button>';
    dialog.firstChild.focus();
    expect(tab().defaultPrevented).toBe(true);
    expect(tab(true).defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(dialog.firstChild);
});
it('uses the checked radio as the group tab stop and skips hidden controls', () => {
    dialog.innerHTML = '<input type="radio" name="choice"><input type="radio" name="choice" checked><button>Last</button><button style="visibility:hidden">Hidden</button>';
    dialog.querySelector('button').focus();
    tab();
    expect(document.activeElement).toBe(dialog.querySelector('input:checked'));
    tab(true);
    expect(document.activeElement).toBe(dialog.querySelector('button'));
});
it('does not intercept modified shortcuts or Tab already handled by a widget', () => {
    dialog.innerHTML = '<button>Only</button>';
    dialog.firstChild.focus();
    const focus = vi.spyOn(dialog.firstChild, 'focus');
    const shortcut = new KeyboardEvent('keydown', { key: 'Tab', ctrlKey: true, cancelable: true });
    wrapModalFocus(shortcut, dialog);
    expect(shortcut.defaultPrevented).toBe(false);
    const handled = new KeyboardEvent('keydown', { key: 'Tab', cancelable: true });
    handled.preventDefault();
    wrapModalFocus(handled, dialog);
    expect(focus).not.toHaveBeenCalled();
});
