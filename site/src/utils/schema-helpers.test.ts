import { describe, it, expect } from "vitest";
import {
    getDocumentation,
    stripNamespacePrefix,
    resolveTypeName,
    collectElementsFlat,
    collectElementsForTree,
    resolveChildren,
    checkExpandability,
    hasElementChildren,
} from "./schema-helpers";

// ─── Test Helpers ────────────────────────────────────────────

const XS = "http://www.w3.org/2001/XMLSchema";

/** Parse an XML string and return the document element. */
function parseXml(xmlString: string): Element {
    const parser = new DOMParser();
    const doc = parser.parseFromString(xmlString, "application/xml");
    return doc.documentElement;
}

/** Build a definitions map from a schema element (matching loadSchema logic). */
function buildDefs(schemaEl: Element): Record<string, Element> {
    const defs: Record<string, Element> = {};
    for (const child of Array.from(schemaEl.children)) {
        const defName = child.getAttribute("name");
        if (defName) {
            defs[`${child.localName}:${defName}`] = child;
        }
    }
    return defs;
}

// ─── getDocumentation ────────────────────────────────────────

describe("getDocumentation", () => {
    it("returns documentation text from annotation/documentation", () => {
        const el = parseXml(`
            <element xmlns="${XS}">
                <annotation><documentation>Hello world</documentation></annotation>
            </element>
        `);
        expect(getDocumentation(el)).toBe("Hello world");
    });

    it("returns empty string when no annotation exists", () => {
        const el = parseXml(`<element xmlns="${XS}" />`);
        expect(getDocumentation(el)).toBe("");
    });

    it("returns empty string when annotation has no documentation child", () => {
        const el = parseXml(`
            <element xmlns="${XS}">
                <annotation><appinfo>info</appinfo></annotation>
            </element>
        `);
        expect(getDocumentation(el)).toBe("");
    });

    it("returns empty string for empty documentation element", () => {
        const el = parseXml(`
            <element xmlns="${XS}">
                <annotation><documentation></documentation></annotation>
            </element>
        `);
        expect(getDocumentation(el)).toBe("");
    });

    it("handles multiline documentation", () => {
        const el = parseXml(`
            <element xmlns="${XS}">
                <annotation><documentation>Line 1
Line 2</documentation></annotation>
            </element>
        `);
        expect(getDocumentation(el)).toContain("Line 1");
        expect(getDocumentation(el)).toContain("Line 2");
    });
});

// ─── stripNamespacePrefix ────────────────────────────────────

describe("stripNamespacePrefix", () => {
    it("strips prefix from qualified name", () => {
        expect(stripNamespacePrefix("xs:string")).toBe("string");
    });

    it("returns name unchanged if no prefix", () => {
        expect(stripNamespacePrefix("MyType")).toBe("MyType");
    });

    it("returns null for null input", () => {
        expect(stripNamespacePrefix(null)).toBeNull();
    });

    it("returns null for empty string (falsy)", () => {
        expect(stripNamespacePrefix("")).toBeNull();
    });

    it("handles multiple colons (takes last segment)", () => {
        expect(stripNamespacePrefix("a:b:c")).toBe("c");
    });
});

// ─── resolveTypeName ─────────────────────────────────────────

describe("resolveTypeName", () => {
    it("returns type attribute stripped of prefix", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" type="xs:FooType" />
            </schema>
        `);
        const el = schema.children[0];
        expect(resolveTypeName(el, {})).toBe("FooType");
    });

    it("returns bare type name without prefix", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" type="FooType" />
            </schema>
        `);
        const el = schema.children[0];
        expect(resolveTypeName(el, {})).toBe("FooType");
    });

    it("follows ref to find type on referenced element", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Bar" type="BarType" />
                <element name="Foo" ref="Bar" />
            </schema>
        `);
        const defs = buildDefs(schema);
        const refEl = schema.children[1]; // the ref="Bar" element
        expect(resolveTypeName(refEl, defs)).toBe("BarType");
    });

    it("returns null for element with no type or ref", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" />
            </schema>
        `);
        const el = schema.children[0];
        expect(resolveTypeName(el, {})).toBeNull();
    });

    it("follows namespaced ref to find type", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Bar" type="ns:BarType" />
                <element name="Foo" ref="ns:Bar" />
            </schema>
        `);
        const defs = buildDefs(schema);
        const refEl = schema.children[1];
        expect(resolveTypeName(refEl, defs)).toBe("BarType");
    });
});

// ─── collectElementsFlat ─────────────────────────────────────

describe("collectElementsFlat", () => {
    it("collects direct element children from sequence", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="FooType">
                    <sequence>
                        <element name="A" />
                        <element name="B" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const fooType = defs["complexType:FooType"];
        const result = collectElementsFlat(fooType, defs);
        expect(result).toHaveLength(2);
        expect(result[0].getAttribute("name")).toBe("A");
        expect(result[1].getAttribute("name")).toBe("B");
    });

    it("recurses into choice groups (flattening them)", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="FooType">
                    <sequence>
                        <choice>
                            <element name="X" />
                            <element name="Y" />
                        </choice>
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const fooType = defs["complexType:FooType"];
        const result = collectElementsFlat(fooType, defs);
        expect(result).toHaveLength(2);
        expect(result[0].getAttribute("name")).toBe("X");
        expect(result[1].getAttribute("name")).toBe("Y");
    });

    it("resolves complexContent/extension base types", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="BaseType">
                    <sequence>
                        <element name="BaseField" />
                    </sequence>
                </complexType>
                <complexType name="DerivedType">
                    <complexContent>
                        <extension base="BaseType">
                            <sequence>
                                <element name="DerivedField" />
                            </sequence>
                        </extension>
                    </complexContent>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const derived = defs["complexType:DerivedType"];
        const result = collectElementsFlat(derived, defs);
        expect(result).toHaveLength(2);
        expect(result[0].getAttribute("name")).toBe("BaseField");
        expect(result[1].getAttribute("name")).toBe("DerivedField");
    });

    it("caches results for named complexTypes", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="FooType">
                    <sequence>
                        <element name="A" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const fooType = defs["complexType:FooType"];
        const cache = new Map<string, Element[]>();

        const result1 = collectElementsFlat(fooType, defs, cache);
        expect(cache.has("complexType:FooType")).toBe(true);

        const result2 = collectElementsFlat(fooType, defs, cache);
        expect(result1).toBe(result2); // same reference from cache
    });

    it("handles nested sequences", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="FooType">
                    <sequence>
                        <element name="A" />
                        <sequence>
                            <element name="B" />
                        </sequence>
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const result = collectElementsFlat(
            defs["complexType:FooType"],
            defs,
        );
        expect(result).toHaveLength(2);
    });

    it("returns empty for empty complexType", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="EmptyType" />
            </schema>
        `);
        const defs = buildDefs(schema);
        const result = collectElementsFlat(
            defs["complexType:EmptyType"],
            defs,
        );
        expect(result).toHaveLength(0);
    });
});

// ─── collectElementsForTree ──────────────────────────────────

describe("collectElementsForTree", () => {
    it("collects direct element children from sequence", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="FooType">
                    <sequence>
                        <element name="A" />
                        <element name="B" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const fooType = defs["complexType:FooType"];
        const result = collectElementsForTree(fooType, defs);
        expect(result).toHaveLength(2);
        expect(result[0].getAttribute("name")).toBe("A");
        expect(result[1].getAttribute("name")).toBe("B");
    });

    it("preserves choice nodes as opaque elements (not flattened)", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="FooType">
                    <sequence>
                        <choice>
                            <element name="X" />
                            <element name="Y" />
                        </choice>
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const fooType = defs["complexType:FooType"];
        const result = collectElementsForTree(fooType, defs);
        // choice is kept as-is, not flattened into X and Y
        expect(result).toHaveLength(1);
        expect(result[0].localName).toBe("choice");
    });

    it("resolves complexContent/extension base types", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="BaseType">
                    <sequence>
                        <element name="BaseField" />
                    </sequence>
                </complexType>
                <complexType name="DerivedType">
                    <complexContent>
                        <extension base="BaseType">
                            <sequence>
                                <element name="DerivedField" />
                            </sequence>
                        </extension>
                    </complexContent>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const derived = defs["complexType:DerivedType"];
        const result = collectElementsForTree(derived, defs);
        expect(result).toHaveLength(2);
        expect(result[0].getAttribute("name")).toBe("BaseField");
        expect(result[1].getAttribute("name")).toBe("DerivedField");
    });

    it("preserves duplicate-named sibling elements (regression: PGPKeyPacket)", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="KeyInfoType">
                    <sequence>
                        <element name="KeyName" />
                        <element name="PGPKeyPacket" />
                        <element name="PGPKeyPacket" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const result = collectElementsForTree(
            defs["complexType:KeyInfoType"],
            defs,
        );
        expect(result).toHaveLength(3);
        expect(result[0].getAttribute("name")).toBe("KeyName");
        expect(result[1].getAttribute("name")).toBe("PGPKeyPacket");
        expect(result[2].getAttribute("name")).toBe("PGPKeyPacket");
    });

    it("flattens through all containers", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="FooType">
                    <all>
                        <element name="A" />
                        <element name="B" />
                    </all>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const result = collectElementsForTree(
            defs["complexType:FooType"],
            defs,
        );
        expect(result).toHaveLength(2);
    });
});

// ─── resolveChildren ─────────────────────────────────────────

describe("resolveChildren", () => {
    it("resolves children from a named type", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" type="FooType" />
                <complexType name="FooType">
                    <sequence>
                        <element name="Bar" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const fooEl = defs["element:Foo"];
        const result = resolveChildren(fooEl, defs);
        expect(result).toHaveLength(1);
        expect(result[0].getAttribute("name")).toBe("Bar");
    });

    it("resolves children from inline complexType", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo">
                    <complexType>
                        <sequence>
                            <element name="Inner" />
                        </sequence>
                    </complexType>
                </element>
            </schema>
        `);
        const defs = buildDefs(schema);
        const fooEl = defs["element:Foo"];
        const result = resolveChildren(fooEl, defs);
        expect(result).toHaveLength(1);
        expect(result[0].getAttribute("name")).toBe("Inner");
    });

    it("resolves children from a ref element", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Target" type="TargetType" />
                <complexType name="TargetType">
                    <sequence>
                        <element name="Child" />
                    </sequence>
                </complexType>
                <element name="Ref" ref="Target" />
            </schema>
        `);
        const defs = buildDefs(schema);
        const refEl = defs["element:Ref"];
        const result = resolveChildren(refEl, defs);
        expect(result).toHaveLength(1);
        expect(result[0].getAttribute("name")).toBe("Child");
    });

    it("resolves children from ref with inline complexType", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Target">
                    <complexType>
                        <sequence>
                            <element name="Inner" />
                        </sequence>
                    </complexType>
                </element>
                <element name="Ref" ref="Target" />
            </schema>
        `);
        const defs = buildDefs(schema);
        const refEl = defs["element:Ref"];
        const result = resolveChildren(refEl, defs);
        expect(result).toHaveLength(1);
        expect(result[0].getAttribute("name")).toBe("Inner");
    });

    it("returns empty array for simple types", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" type="xs:string" />
            </schema>
        `);
        const defs = buildDefs(schema);
        const fooEl = defs["element:Foo"];
        const result = resolveChildren(fooEl, defs);
        expect(result).toHaveLength(0);
    });

    it("returns empty array for element with no type", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" />
            </schema>
        `);
        const defs = buildDefs(schema);
        const fooEl = defs["element:Foo"];
        const result = resolveChildren(fooEl, defs);
        expect(result).toHaveLength(0);
    });

    it("uses cache when provided", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" type="FooType" />
                <complexType name="FooType">
                    <sequence>
                        <element name="A" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        const cache = new Map<string, Element[]>();
        resolveChildren(defs["element:Foo"], defs, cache);
        expect(cache.has("complexType:FooType")).toBe(true);
    });
});

// ─── checkExpandability ──────────────────────────────────────

describe("checkExpandability", () => {
    it("returns true for elements with complexType children", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" type="FooType" />
                <complexType name="FooType">
                    <sequence>
                        <element name="Bar" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        expect(checkExpandability(defs["element:Foo"], defs)).toBe(true);
    });

    it("returns false for simple type elements", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" type="FooSimple" />
                <simpleType name="FooSimple" />
            </schema>
        `);
        const defs = buildDefs(schema);
        expect(checkExpandability(defs["element:Foo"], defs)).toBe(false);
    });

    it("returns true for elements with inline complexType", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo">
                    <complexType>
                        <sequence>
                            <element name="Inner" />
                        </sequence>
                    </complexType>
                </element>
            </schema>
        `);
        const defs = buildDefs(schema);
        expect(checkExpandability(defs["element:Foo"], defs)).toBe(true);
    });

    it("handles circular refs via visited set", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="A" ref="B" />
                <element name="B" ref="A" />
            </schema>
        `);
        const defs = buildDefs(schema);
        // Should not infinite loop, returns true for circular refs
        expect(checkExpandability(defs["element:A"], defs)).toBe(true);
    });

    it("follows ref attributes to check referenced elements", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Ref" ref="Target" />
                <element name="Target" type="TargetType" />
                <complexType name="TargetType">
                    <sequence>
                        <element name="Child" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        expect(checkExpandability(defs["element:Ref"], defs)).toBe(true);
    });

    it("returns false for element with no type and no children", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Foo" />
            </schema>
        `);
        const defs = buildDefs(schema);
        expect(checkExpandability(defs["element:Foo"], defs)).toBe(false);
    });

    it("follows namespaced ref attributes (strips prefix)", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Ref" ref="ns:Target" />
                <element name="Target" type="TargetType" />
                <complexType name="TargetType">
                    <sequence>
                        <element name="Child" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);
        expect(checkExpandability(defs["element:Ref"], defs)).toBe(true);
    });

    it("returns false for ref with no matching definition", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <element name="Ref" ref="NonExistent" />
            </schema>
        `);
        const defs = buildDefs(schema);
        expect(checkExpandability(defs["element:Ref"], defs)).toBe(false);
    });
});

// ─── hasElementChildren ──────────────────────────────────────

describe("hasElementChildren", () => {
    it("returns true when element children exist in sequence", () => {
        const el = parseXml(`
            <complexType xmlns="${XS}">
                <sequence>
                    <element name="A" />
                </sequence>
            </complexType>
        `);
        expect(hasElementChildren(el, {})).toBe(true);
    });

    it("returns false for empty complexType", () => {
        const el = parseXml(`<complexType xmlns="${XS}" />`);
        expect(hasElementChildren(el, {})).toBe(false);
    });

    it("finds elements inside choice", () => {
        const el = parseXml(`
            <complexType xmlns="${XS}">
                <choice>
                    <element name="X" />
                </choice>
            </complexType>
        `);
        expect(hasElementChildren(el, {})).toBe(true);
    });

    it("finds elements inside all", () => {
        const el = parseXml(`
            <complexType xmlns="${XS}">
                <all>
                    <element name="X" />
                </all>
            </complexType>
        `);
        expect(hasElementChildren(el, {})).toBe(true);
    });

    it("checks through complexContent/extension base types", () => {
        const schema = parseXml(`
            <schema xmlns="${XS}">
                <complexType name="BaseType">
                    <sequence>
                        <element name="A" />
                    </sequence>
                </complexType>
            </schema>
        `);
        const defs = buildDefs(schema);

        const derived = parseXml(`
            <complexType xmlns="${XS}">
                <complexContent>
                    <extension base="BaseType" />
                </complexContent>
            </complexType>
        `);
        expect(hasElementChildren(derived, defs)).toBe(true);
    });

    it("respects BFS limit", () => {
        // Deeply nested structure that exceeds limit
        const el = parseXml(`
            <complexType xmlns="${XS}">
                <sequence>
                    <sequence>
                        <sequence>
                            <element name="Deep" />
                        </sequence>
                    </sequence>
                </sequence>
            </complexType>
        `);
        // With a very low BFS limit it may not find the element
        expect(hasElementChildren(el, {}, 1)).toBe(false);
        // With sufficient limit it finds it
        expect(hasElementChildren(el, {}, 50)).toBe(true);
    });
});
