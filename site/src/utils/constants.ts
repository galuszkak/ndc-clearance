/** Prefix used in IATA message naming convention */
export const IATA_PREFIX = "IATA_";

/** Schema search configuration */
export const SEARCH = {
    /** Number of nodes processed per async chunk before yielding */
    CHUNK_SIZE: 50,
    /** Maximum number of nodes to visit during search */
    MAX_NODES: 2000,
    /** Maximum number of visible search results */
    MAX_RESULTS: 50,
    /** Debounce delay for search input (ms) */
    DEBOUNCE_MS: 300,
    /** Minimum query length before search highlights appear in the tree */
    MIN_QUERY_LENGTH: 3,
} as const;

/** Tree zoom configuration */
export const ZOOM = {
    MIN: 50,
    MAX: 200,
    DEFAULT: 100,
    STEP: 10,
} as const;

/** Default depth for initial tree expansion */
export const DEFAULT_EXPAND_DEPTH = 2;

/** Meta description truncation length */
export const META_DESCRIPTION_MAX_LENGTH = 155;

/** Modal DOM element IDs */
export const MODAL_IDS = {
    EXAMPLES: "examples_modal",
    FLOWS: "flows_modal",
    DOWNLOAD: "download_modal",
    CLAUDE_CODE: "modal_claude_code",
    CLAUDE_DESKTOP: "modal_claude_desktop",
    VSCODE: "modal_vscode",
    GEMINI: "modal_gemini",
    OPENCODE: "modal_opencode",
} as const;
