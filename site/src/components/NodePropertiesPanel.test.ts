import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/svelte";
import NodePropertiesPanel from "./NodePropertiesPanel.svelte";

/** Parse a minimal XML element for use as selectedNode prop. */
function makeElement(
    localName: string,
    attrs: Record<string, string> = {},
): Element {
    const doc = new DOMParser().parseFromString(
        `<${localName} xmlns="http://www.w3.org/2001/XMLSchema" ${Object.entries(attrs)
            .map(([k, v]) => `${k}="${v}"`)
            .join(" ")} />`,
        "application/xml",
    );
    return doc.documentElement;
}

describe("NodePropertiesPanel", () => {
    it("shows placeholder text when no node is selected", () => {
        render(NodePropertiesPanel, {
            props: { selectedNode: null, selectedHelper: {} },
        });

        expect(
            screen.getByText("Select an element to view details."),
        ).toBeInTheDocument();
    });

    it("shows element name and type when a node is selected", () => {
        const node = makeElement("element", { name: "Traveler" });
        render(NodePropertiesPanel, {
            props: {
                selectedNode: node,
                selectedHelper: {
                    typeName: "TravelerType",
                    min: "1",
                    max: "1",
                    doc: "A traveler object.",
                },
            },
        });

        expect(screen.getByText("Traveler")).toBeInTheDocument();
        // TravelerType appears in both header and constraints — check both exist
        expect(screen.getAllByText("TravelerType")).toHaveLength(2);
        expect(screen.getByText("A traveler object.")).toBeInTheDocument();
    });

    it("shows REQUIRED badge when min is not 0", () => {
        const node = makeElement("element", { name: "Foo" });
        render(NodePropertiesPanel, {
            props: {
                selectedNode: node,
                selectedHelper: { min: "1", max: "1", typeName: "FooType" },
            },
        });

        expect(screen.getByText("REQUIRED")).toBeInTheDocument();
    });

    it("shows OPTIONAL badge when min is 0", () => {
        const node = makeElement("element", { name: "Foo" });
        render(NodePropertiesPanel, {
            props: {
                selectedNode: node,
                selectedHelper: { min: "0", max: "1", typeName: "FooType" },
            },
        });

        expect(screen.getByText("OPTIONAL")).toBeInTheDocument();
    });

    it("shows LIST badge when max is unbounded", () => {
        const node = makeElement("element", { name: "Foo" });
        render(NodePropertiesPanel, {
            props: {
                selectedNode: node,
                selectedHelper: {
                    min: "0",
                    max: "unbounded",
                    typeName: "FooType",
                },
            },
        });

        expect(screen.getByText("LIST")).toBeInTheDocument();
    });

    it("shows MESSAGE badge for RQ/RS names", () => {
        const node = makeElement("element", { name: "AirShoppingRQ" });
        render(NodePropertiesPanel, {
            props: {
                selectedNode: node,
                selectedHelper: { min: "1", max: "1" },
            },
        });

        expect(screen.getByText("MESSAGE")).toBeInTheDocument();
    });

    it("shows CHOICE badge for choice elements", () => {
        const node = makeElement("choice");
        render(NodePropertiesPanel, {
            props: {
                selectedNode: node,
                selectedHelper: { min: "1", max: "1" },
            },
        });

        expect(screen.getByText("CHOICE")).toBeInTheDocument();
    });

    it("displays infinity symbol for unbounded max", () => {
        const node = makeElement("element", { name: "Items" });
        const { container } = render(NodePropertiesPanel, {
            props: {
                selectedNode: node,
                selectedHelper: {
                    min: "0",
                    max: "unbounded",
                    typeName: "ItemType",
                },
            },
        });

        // The occurrence text is "0 .. ∞" inside a font-mono element
        const occurrences = container.querySelector(".font-mono.font-bold");
        expect(occurrences?.textContent).toContain("0");
        expect(occurrences?.textContent).toContain("∞");
    });
});
