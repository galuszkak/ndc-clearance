import { describe, it, expect, vi, beforeEach } from "vitest";
import { openModal } from "./modal";

describe("openModal", () => {
    beforeEach(() => {
        document.body.innerHTML = "";
    });

    it("calls showModal on an existing dialog element", () => {
        const dialog = document.createElement("dialog");
        dialog.id = "test_modal";
        dialog.showModal = vi.fn();
        document.body.appendChild(dialog);

        openModal("test_modal");
        expect(dialog.showModal).toHaveBeenCalledOnce();
    });

    it("does nothing for a nonexistent ID", () => {
        // Should not throw
        expect(() => openModal("nonexistent")).not.toThrow();
    });

    it("throws for a non-dialog element (no showModal method)", () => {
        const div = document.createElement("div");
        div.id = "not_a_dialog";
        document.body.appendChild(div);

        // The cast to HTMLDialogElement means showModal is called on a div, which throws
        expect(() => openModal("not_a_dialog")).toThrow();
    });
});
