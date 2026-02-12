import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/svelte";
import XmlSourceView from "./XmlSourceView.svelte";
import type { LoadedSchemaFile } from "../utils/types";

function makeFile(name: string): LoadedSchemaFile {
    const doc = new DOMParser().parseFromString(
        "<schema/>",
        "application/xml",
    );
    return { name, url: `/${name}`, content: `<schema/>`, doc };
}

describe("XmlSourceView", () => {
    it("shows loading spinner when loading", () => {
        const { container } = render(XmlSourceView, {
            props: {
                loadedFiles: [],
                selectedFileIndex: 0,
                rawXml: "",
                highlightedXml: "",
                loading: true,
                onSelectFile: vi.fn(),
            },
        });

        expect(
            container.querySelector(".loading-spinner"),
        ).toBeInTheDocument();
    });

    it("renders highlighted XML when provided", () => {
        const { container } = render(XmlSourceView, {
            props: {
                loadedFiles: [],
                selectedFileIndex: 0,
                rawXml: "<root/>",
                highlightedXml: '<span class="hljs-tag">&lt;root/&gt;</span>',
                loading: false,
                onSelectFile: vi.fn(),
            },
        });

        expect(
            container.querySelector("code.language-xml"),
        ).toBeInTheDocument();
    });

    it("shows Highlighting message when no highlighted XML yet", () => {
        render(XmlSourceView, {
            props: {
                loadedFiles: [],
                selectedFileIndex: 0,
                rawXml: "<root/>",
                highlightedXml: "",
                loading: false,
                onSelectFile: vi.fn(),
            },
        });

        expect(screen.getByText(/Highlighting/)).toBeInTheDocument();
    });

    it("shows file list sidebar when multiple files loaded", () => {
        const files = [makeFile("main.xsd"), makeFile("types.xsd")];

        render(XmlSourceView, {
            props: {
                loadedFiles: files,
                selectedFileIndex: 0,
                rawXml: "<schema/>",
                highlightedXml: "<schema/>",
                loading: false,
                onSelectFile: vi.fn(),
            },
        });

        expect(
            screen.getByRole("tablist", { name: "Schema files" }),
        ).toBeInTheDocument();
        expect(screen.getAllByRole("tab")).toHaveLength(2);
        expect(screen.getByText("main.xsd")).toBeInTheDocument();
        expect(screen.getByText("types.xsd")).toBeInTheDocument();
    });

    it("does not show file sidebar for single file", () => {
        render(XmlSourceView, {
            props: {
                loadedFiles: [makeFile("schema.xsd")],
                selectedFileIndex: 0,
                rawXml: "<schema/>",
                highlightedXml: "<schema/>",
                loading: false,
                onSelectFile: vi.fn(),
            },
        });

        expect(
            screen.queryByRole("tablist", { name: "Schema files" }),
        ).not.toBeInTheDocument();
    });

    it("marks selected file tab as aria-selected", () => {
        const files = [makeFile("main.xsd"), makeFile("types.xsd")];

        render(XmlSourceView, {
            props: {
                loadedFiles: files,
                selectedFileIndex: 1,
                rawXml: "<schema/>",
                highlightedXml: "<schema/>",
                loading: false,
                onSelectFile: vi.fn(),
            },
        });

        const tabs = screen.getAllByRole("tab");
        expect(tabs[0]).toHaveAttribute("aria-selected", "false");
        expect(tabs[1]).toHaveAttribute("aria-selected", "true");
    });

    it("has a copy button with aria-label", () => {
        render(XmlSourceView, {
            props: {
                loadedFiles: [],
                selectedFileIndex: 0,
                rawXml: "<root/>",
                highlightedXml: "<root/>",
                loading: false,
                onSelectFile: vi.fn(),
            },
        });

        expect(
            screen.getByRole("button", { name: "Copy XML to clipboard" }),
        ).toBeInTheDocument();
    });
});
