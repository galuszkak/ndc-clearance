/**
 * Pure helper functions for XSD schema traversal.
 *
 * Extracted from SchemaExplorer.svelte and SchemaNode.svelte to
 * eliminate duplication and enable unit testing.
 */

import type { IconType } from "./types";

/**
 * Extract xs:documentation text from an XSD element's xs:annotation child.
 * Returns empty string if no documentation is found.
 */
export function getDocumentation(el: Element): string {
    const annotation = Array.from(el.children).find(
        (c) => c.localName === "annotation",
    );
    if (annotation) {
        const docNode = Array.from(annotation.children).find(
            (c) => c.localName === "documentation",
        );
        return docNode ? docNode.textContent || "" : "";
    }
    return "";
}

/**
 * Strip namespace prefix from an XSD qualified name.
 * e.g. "xs:string" → "string", "MyType" → "MyType", null → null
 */
export function stripNamespacePrefix(name: string | null): string | null {
    if (!name) return null;
    return name.includes(":") ? name.split(":").pop()! : name;
}

/**
 * Resolve the effective type name for an element, following ref indirections.
 * Returns the bare type name (without namespace prefix) or null.
 */
export function resolveTypeName(
    node: Element,
    defs: Record<string, Element>,
): string | null {
    let typeName = stripNamespacePrefix(node.getAttribute("type"));

    if (node.getAttribute("ref")) {
        const refName = stripNamespacePrefix(node.getAttribute("ref"));
        if (refName) {
            const def = defs[`element:${refName}`];
            if (def) {
                const defType = def.getAttribute("type");
                if (defType) {
                    typeName = stripNamespacePrefix(defType);
                }
            }
        }
    }

    return typeName;
}

/**
 * Recursively collect xs:element descendants, flattening through
 * sequence/choice/all/complexContent/extension containers.
 *
 * This is the "search" variant: it recurses into choice groups
 * (flattening them) and supports caching via an optional Map.
 *
 * Used by SchemaExplorer for search traversal.
 */
export function collectElementsFlat(
    root: Element,
    defs: Record<string, Element>,
    cache?: Map<string, Element[]>,
): Element[] {
    let cacheKey = "";
    const rootTypeName = root.getAttribute("name");

    if (
        ["complexType", "simpleType"].includes(root.localName) &&
        rootTypeName
    ) {
        cacheKey = `${root.localName}:${rootTypeName}`;
        if (cache?.has(cacheKey)) {
            return cache.get(cacheKey)!;
        }
    }

    const els: Element[] = [];
    for (const child of Array.from(root.children)) {
        if (child.localName === "element") {
            els.push(child);
        } else if (
            ["sequence", "choice", "all"].includes(child.localName)
        ) {
            els.push(...collectElementsFlat(child, defs, cache));
        } else if (child.localName === "complexContent") {
            for (const cc of Array.from(child.children)) {
                if (
                    cc.localName === "extension" ||
                    cc.localName === "restriction"
                ) {
                    const base = stripNamespacePrefix(cc.getAttribute("base"));
                    if (base) {
                        const baseDef = defs[`complexType:${base}`];
                        if (baseDef) {
                            els.push(
                                ...collectElementsFlat(baseDef, defs, cache),
                            );
                        }
                    }
                    els.push(...collectElementsFlat(cc, defs, cache));
                }
            }
        } else if (
            child.localName === "complexType" ||
            child.localName === "simpleType"
        ) {
            els.push(...collectElementsFlat(child, defs, cache));
        }
    }

    if (cacheKey && cache) {
        cache.set(cacheKey, els);
    }
    return els;
}

/**
 * Collect direct children for tree rendering.
 *
 * This is the "tree" variant: choice nodes are preserved as opaque
 * elements (they render as "One Of" containers in SchemaNode).
 * Only sequence/all are flattened.
 *
 * Used by SchemaNode for tree rendering.
 */
export function collectElementsForTree(
    root: Element,
    definitions: Record<string, Element>,
): Element[] {
    const els: Element[] = [];
    for (const child of Array.from(root.children)) {
        if (child.localName === "element") {
            els.push(child);
        } else if (child.localName === "choice") {
            els.push(child);
        } else if (["sequence", "all"].includes(child.localName)) {
            els.push(...collectElementsForTree(child, definitions));
        } else if (child.localName === "complexContent") {
            for (const cc of Array.from(child.children)) {
                if (
                    cc.localName === "extension" ||
                    cc.localName === "restriction"
                ) {
                    const base = stripNamespacePrefix(cc.getAttribute("base"));
                    if (base) {
                        const baseDef = definitions[`complexType:${base}`];
                        if (baseDef) {
                            els.push(
                                ...collectElementsForTree(baseDef, definitions),
                            );
                        }
                    }
                    els.push(...collectElementsForTree(cc, definitions));
                }
            }
        } else if (
            child.localName === "complexType" ||
            child.localName === "simpleType"
        ) {
            els.push(...collectElementsForTree(child, definitions));
        }
    }
    return els;
}

/**
 * Resolve the children of a given element node by following type
 * references and ref attributes. Used by search traversal.
 */
export function resolveChildren(
    node: Element,
    defs: Record<string, Element>,
    cache?: Map<string, Element[]>,
): Element[] {
    let typeName = stripNamespacePrefix(node.getAttribute("type"));

    if (node.getAttribute("ref")) {
        const refName = stripNamespacePrefix(node.getAttribute("ref"));
        if (refName) {
            const def = defs[`element:${refName}`];
            if (def) {
                typeName =
                    stripNamespacePrefix(def.getAttribute("type")) || typeName;
                if (!typeName) {
                    const inline = Array.from(def.children).find(
                        (c) => c.localName === "complexType",
                    );
                    if (inline) {
                        return collectElementsFlat(inline, defs, cache);
                    }
                }
            }
        }
    }

    let typeDefinition = typeName
        ? defs[`complexType:${typeName}`] || defs[`simpleType:${typeName}`]
        : null;
    if (!typeDefinition) {
        typeDefinition =
            Array.from(node.children).find(
                (c) =>
                    c.localName === "complexType" ||
                    c.localName === "simpleType",
            ) || null;
    }

    if (typeDefinition) {
        return collectElementsFlat(typeDefinition, defs, cache);
    }
    return [];
}

/**
 * Check whether a given element can be expanded in the tree
 * (i.e., has element children when its type is resolved).
 */
export function checkExpandability(
    root: Element,
    defs: Record<string, Element>,
    visited = new Set<string>(),
): boolean {
    const tn = stripNamespacePrefix(root.getAttribute("type"));

    if (root.getAttribute("ref")) {
        const refName = stripNamespacePrefix(root.getAttribute("ref"));
        if (!refName) return false;
        if (visited.has(refName)) return true;
        visited.add(refName);
        const distinct = defs[`element:${refName}`];
        if (distinct) return checkExpandability(distinct, defs, visited);
    }

    if (tn) {
        if (defs[`simpleType:${tn}`]) return false;
        const complex = defs[`complexType:${tn}`];
        if (complex) return hasElementChildren(complex, defs);
    }

    const inline = Array.from(root.children).find(
        (c) => c.localName === "complexType",
    );
    if (inline) return hasElementChildren(inline, defs);

    return false;
}

/**
 * BFS check whether a type definition has any element descendants.
 */
export function hasElementChildren(
    root: Element,
    defs: Record<string, Element>,
    bfsLimit = 50,
    visited = new Set<string>(),
): boolean {
    const q = [root];
    let limit = 0;
    while (q.length > 0 && limit < bfsLimit) {
        const curr = q.shift()!;
        limit++;
        for (const child of Array.from(curr.children)) {
            if (child.localName === "element") return true;
            if (["sequence", "choice", "all"].includes(child.localName)) {
                q.push(child);
            } else if (child.localName === "complexContent") {
                for (const cc of Array.from(child.children)) {
                    if (
                        cc.localName === "extension" ||
                        cc.localName === "restriction"
                    ) {
                        const base = stripNamespacePrefix(cc.getAttribute("base"));
                        if (base && !visited.has(base)) {
                            visited.add(base);
                            if (
                                defs[`complexType:${base}`] &&
                                hasElementChildren(
                                    defs[`complexType:${base}`],
                                    defs,
                                    bfsLimit,
                                    visited,
                                )
                            )
                                return true;
                        }
                        q.push(cc);
                    }
                }
            }
        }
    }
    return false;
}

/**
 * Check if a name or documentation string matches a search query.
 * docMatch is only true when nameMatch is false (name takes precedence).
 */
export function matchesSearch(
    name: string,
    documentation: string,
    query: string,
): { nameMatch: boolean; docMatch: boolean } {
    if (!query) return { nameMatch: false, docMatch: false };
    const lowerQuery = query.toLowerCase();
    const nameMatch = name.toLowerCase().includes(lowerQuery);
    const docMatch =
        !nameMatch && documentation
            ? documentation.toLowerCase().includes(lowerQuery)
            : false;
    return { nameMatch, docMatch };
}

/**
 * Determine the icon type for a schema element based on its name and type.
 */
export function getIconType(
    name: string,
    typeName: string | null,
): IconType {
    if (name.endsWith("RQ") || name.endsWith("RS")) return "message";
    if (typeName) return "typed";
    return "element";
}
