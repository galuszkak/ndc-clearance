/**
 * Open an HTML dialog element by its DOM ID.
 * Safe to call with non-existent IDs (no-op).
 */
export function openModal(id: string): void {
    const el = document.getElementById(id) as HTMLDialogElement | null;
    el?.showModal();
}
