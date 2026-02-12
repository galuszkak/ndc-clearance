import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import TreeToolbar from "./TreeToolbar.svelte";

describe("TreeToolbar", () => {
    it("displays the current zoom level", () => {
        render(TreeToolbar, {
            props: {
                zoom: 100,
                onExpandAll: vi.fn(),
                onCollapseAll: vi.fn(),
            },
        });

        expect(screen.getByText("100%")).toBeInTheDocument();
    });

    it("calls onExpandAll when Expand All is clicked", async () => {
        const onExpandAll = vi.fn();
        const user = userEvent.setup();

        render(TreeToolbar, {
            props: {
                zoom: 100,
                onExpandAll,
                onCollapseAll: vi.fn(),
            },
        });

        await user.click(
            screen.getByRole("button", { name: "Expand all tree nodes" }),
        );
        expect(onExpandAll).toHaveBeenCalledOnce();
    });

    it("calls onCollapseAll when Collapse All is clicked", async () => {
        const onCollapseAll = vi.fn();
        const user = userEvent.setup();

        render(TreeToolbar, {
            props: {
                zoom: 100,
                onExpandAll: vi.fn(),
                onCollapseAll,
            },
        });

        await user.click(
            screen.getByRole("button", { name: "Collapse all tree nodes" }),
        );
        expect(onCollapseAll).toHaveBeenCalledOnce();
    });

    it("has zoom in and zoom out buttons with aria labels", () => {
        render(TreeToolbar, {
            props: {
                zoom: 100,
                onExpandAll: vi.fn(),
                onCollapseAll: vi.fn(),
            },
        });

        expect(
            screen.getByRole("button", { name: "Zoom in" }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Zoom out" }),
        ).toBeInTheDocument();
    });
});
