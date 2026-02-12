/** Schema statistics tracked by the store */
export interface SchemaStats {
    elements: number;
    complexTypes: number;
    simpleTypes: number;
}

/** Icon classification for schema elements */
export type IconType = "message" | "typed" | "element";

/** A search result from the BFS schema search */
export interface SearchResult {
    name: string;
    path: string;
    type: string;
    doc: string;
    matchType: "name" | "doc";
    iconType: IconType;
    typeName?: string;
}

/** A loaded schema file with parsed content */
export interface LoadedSchemaFile {
    name: string;
    content: string;
    url: string;
    doc: XMLDocument;
}

/** Schema file reference (name + URL) */
export interface SchemaFileRef {
    name: string;
    url: string;
}

/** Node selection event detail */
export interface NodeSelectDetail {
    node: Element;
    path: string;
    element?: HTMLElement;
}

/** Selected node helper info for the properties panel */
export interface SelectedNodeHelper {
    typeName?: string;
    min?: string;
    max?: string;
    doc?: string;
    regex?: string;
}

/** Diff item from the API */
export interface DiffItem {
    path: string;
    type: "ADDED" | "REMOVED" | "MODIFIED" | "DOC_CHANGED";
    description: string;
    oldValue?: string;
    newValue?: string;
}

/** Message diff result from the API */
export interface MessageDiff {
    messageName: string;
    differences: DiffItem[];
    status: "ADDED" | "REMOVED" | "CHANGED" | "UNCHANGED";
}

/** Worked example from JSON data */
export interface WorkedExample {
    title: string;
    file: string;
    url: string;
    original_version: string;
    path: string;
}

/** Map of message names to their worked examples */
export type WorkedExamplesMap = Record<string, WorkedExample[]>;
