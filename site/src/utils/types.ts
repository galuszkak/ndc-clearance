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

/** Canonical example record from ndc_content/examples/catalog.json */
export interface ExampleRecord {
    id: string;
    source: "iata" | "custom";
    message: string;
    version: string;
    title: string;
    description: string | null;
    tags: string[];
    file_name: string;
    xml_path: string;
    public_path: string;
    source_url: string | null;
    source_page_id: string | null;
    flow_id: string | null;
    is_active: boolean;
}

/** Canonical examples catalog file */
export interface ExampleCatalog {
    version: number;
    generated_at: string;
    examples: ExampleRecord[];
}

/** Flow step reference shape */
export interface FlowStep {
    step_id: string;
    order: number;
    message: string;
    example_id: string;
    notes?: string;
    optional?: boolean;
}

/** Flow definition shape */
export interface FlowRecord {
    id: string;
    title: string;
    description: string;
    goal: string;
    tags: string[];
    actors: string[];
    status: "draft" | "active" | "deprecated";
    steps: FlowStep[];
}
