import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import SearchPanel from "./SearchPanel.svelte";
import type { SearchResult } from "../utils/types";

function makeResult(overrides: Partial<SearchResult> = {}): SearchResult {
    return {
        name: "Traveler",
        path: "/AirShoppingRQ/Traveler",
        type: "Element",
        doc: "Traveler info",
        matchType: "name",
        iconType: "typed",
        typeName: "TravelerType",
        ...overrides,
    };
}

describe("SearchPanel", () => {
    it("renders search input with placeholder", () => {
        render(SearchPanel, {
            props: {
                query: "",
                searchResults: [],
                isSearching: false,
                onSelectResult: vi.fn(),
            },
        });

        expect(
            screen.getByPlaceholderText("Search elements..."),
        ).toBeInTheDocument();
    });

    it("has combobox role on the search input", () => {
        render(SearchPanel, {
            props: {
                query: "",
                searchResults: [],
                isSearching: false,
                onSelectResult: vi.fn(),
            },
        });

        const input = screen.getByPlaceholderText("Search elements...");
        expect(input).toHaveAttribute("role", "combobox");
    });

    it("shows search results dropdown when results exist", () => {
        const results = [
            makeResult({ name: "Foo", path: "/Foo" }),
            makeResult({ name: "Bar", path: "/Bar" }),
        ];

        render(SearchPanel, {
            props: {
                query: "test",
                searchResults: results,
                isSearching: false,
                onSelectResult: vi.fn(),
            },
        });

        expect(screen.getByRole("listbox")).toBeInTheDocument();
        expect(screen.getAllByRole("option")).toHaveLength(2);
        expect(screen.getByText("Foo")).toBeInTheDocument();
        expect(screen.getByText("Bar")).toBeInTheDocument();
    });

    it("does not show dropdown when no results", () => {
        render(SearchPanel, {
            props: {
                query: "test",
                searchResults: [],
                isSearching: false,
                onSelectResult: vi.fn(),
            },
        });

        expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    });

    it("calls onSelectResult when a result is clicked", async () => {
        const onSelectResult = vi.fn();
        const user = userEvent.setup();
        const result = makeResult();

        render(SearchPanel, {
            props: {
                query: "test",
                searchResults: [result],
                isSearching: false,
                onSelectResult,
            },
        });

        await user.click(screen.getByText("Traveler"));
        expect(onSelectResult).toHaveBeenCalledOnce();
        expect(onSelectResult).toHaveBeenCalledWith(result);
    });

    it("shows result path", () => {
        const result = makeResult({ path: "/Root/Child/Traveler" });
        render(SearchPanel, {
            props: {
                query: "test",
                searchResults: [result],
                isSearching: false,
                onSelectResult: vi.fn(),
            },
        });

        expect(screen.getByText("/Root/Child/Traveler")).toBeInTheDocument();
    });

    it("shows result doc when available", () => {
        const result = makeResult({ doc: "Flight information" });
        render(SearchPanel, {
            props: {
                query: "test",
                searchResults: [result],
                isSearching: false,
                onSelectResult: vi.fn(),
            },
        });

        expect(screen.getByText("Flight information")).toBeInTheDocument();
    });

    it("shows searching status for screen readers", () => {
        render(SearchPanel, {
            props: {
                query: "test",
                searchResults: [],
                isSearching: true,
                onSelectResult: vi.fn(),
            },
        });

        expect(screen.getByText("Searching...")).toBeInTheDocument();
    });

    it("shows result count for screen readers", () => {
        render(SearchPanel, {
            props: {
                query: "test",
                searchResults: [
                    makeResult({ path: "/a" }),
                    makeResult({ path: "/b" }),
                ],
                isSearching: false,
                onSelectResult: vi.fn(),
            },
        });

        expect(screen.getByText("2 results found")).toBeInTheDocument();
    });
});
