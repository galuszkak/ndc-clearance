import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import { flushSync } from "svelte";
import ValidateModal from "./ValidateModal.svelte";

function renderModal() {
    return render(ValidateModal, {
        props: {
            version: "21.3",
            message: "AirShoppingRQ",
            apiBaseUrl: "http://localhost:3000",
        },
    });
}

describe("ValidateModal", () => {
    it("renders the Validate Message button", () => {
        renderModal();
        expect(
            screen.getByRole("button", { name: "Validate Message" }),
        ).toBeInTheDocument();
    });

    it("renders a dialog with correct aria-label", () => {
        const { container } = renderModal();
        const dialog = container.querySelector("dialog");
        expect(dialog).toHaveAttribute("aria-label", "Validate XML Message");
    });

    it("renders textarea for XML input inside dialog", () => {
        const { container } = renderModal();
        const textarea = container.querySelector(
            'textarea[placeholder="Paste your XML message here..."]',
        );
        expect(textarea).toBeInTheDocument();
    });

    it("shows version and message in footer", () => {
        const { container } = renderModal();
        expect(container.textContent).toContain("21.3/AirShoppingRQ");
    });

    it("calls API with correct payload on validate", async () => {
        const user = userEvent.setup();
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: () => Promise.resolve({ valid: true, errors: [] }),
        });
        vi.stubGlobal("fetch", fetchMock);

        const { container } = renderModal();

        // Type into textarea (inside closed dialog — use container query)
        const textarea = container.querySelector("textarea")!;
        await user.type(textarea, "<root/>");

        // Click the inner "Validate" button (inside dialog)
        const validateBtn = container.querySelector(
            "button.btn-primary",
        ) as HTMLButtonElement;
        await user.click(validateBtn);

        expect(fetchMock).toHaveBeenCalledWith(
            "http://localhost:3000/validate",
            expect.objectContaining({
                method: "POST",
                headers: { "Content-Type": "application/json" },
            }),
        );

        const body = JSON.parse(fetchMock.mock.calls[0][1].body);
        expect(body.version).toBe("21.3");
        expect(body.message).toBe("AirShoppingRQ");
        expect(body.xml).toBe("<root/>");

        vi.restoreAllMocks();
    });

    it("shows Valid Message on successful validation", async () => {
        const user = userEvent.setup();
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue({
                ok: true,
                json: () => Promise.resolve({ valid: true, errors: [] }),
            }),
        );

        const { container } = renderModal();
        const textarea = container.querySelector("textarea")!;
        await user.type(textarea, "x");

        const validateBtn = container.querySelector(
            "button.btn-primary",
        ) as HTMLButtonElement;
        await user.click(validateBtn);

        // Wait for async fetch to complete and DOM to update
        await vi.waitFor(() => {
            expect(container.textContent).toContain("Valid Message");
        });

        vi.restoreAllMocks();
    });

    it("shows validation errors from API", async () => {
        const user = userEvent.setup();
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue({
                ok: true,
                json: () =>
                    Promise.resolve({
                        valid: false,
                        errors: ["Missing required element", "Invalid type"],
                    }),
            }),
        );

        const { container } = renderModal();
        const textarea = container.querySelector("textarea")!;
        await user.type(textarea, "x");

        const validateBtn = container.querySelector(
            "button.btn-primary",
        ) as HTMLButtonElement;
        await user.click(validateBtn);

        await vi.waitFor(() => {
            expect(container.textContent).toContain("Missing required element");
            expect(container.textContent).toContain("Invalid type");
        });

        vi.restoreAllMocks();
    });

    it("shows error when API response is malformed", async () => {
        const user = userEvent.setup();
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue({
                ok: true,
                json: () => Promise.resolve({ something: "wrong" }),
            }),
        );

        const { container } = renderModal();
        const textarea = container.querySelector("textarea")!;
        await user.type(textarea, "x");

        const validateBtn = container.querySelector(
            "button.btn-primary",
        ) as HTMLButtonElement;
        await user.click(validateBtn);

        await vi.waitFor(() => {
            expect(container.textContent).toContain("Invalid API response");
        });

        vi.restoreAllMocks();
    });

    it("shows error when network request fails", async () => {
        const user = userEvent.setup();
        vi.stubGlobal(
            "fetch",
            vi.fn().mockResolvedValue({
                ok: false,
                status: 500,
                statusText: "Internal Server Error",
                text: () => Promise.resolve("Server crashed"),
            }),
        );

        const { container } = renderModal();
        const textarea = container.querySelector("textarea")!;
        await user.type(textarea, "x");

        const validateBtn = container.querySelector(
            "button.btn-primary",
        ) as HTMLButtonElement;
        await user.click(validateBtn);

        await vi.waitFor(() => {
            expect(container.textContent).toContain("Server crashed");
        });

        vi.restoreAllMocks();
    });
});
