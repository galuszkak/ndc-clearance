const DEFAULT_API_URL = "http://localhost:8080";
const DEFAULT_MCP_URL = "https://mcp-ndc.sunrisehikers.io/mcp/sse";

/**
 * Returns the backend API base URL (no trailing slash).
 * Set PUBLIC_API_URL in your .env to override the default.
 */
export const API_URL: string =
    import.meta.env.PUBLIC_API_URL || DEFAULT_API_URL;

/**
 * Returns the MCP server URL for AI agent integrations.
 * Set PUBLIC_MCP_URL in your .env to override the default.
 */
export const MCP_URL: string =
    import.meta.env.PUBLIC_MCP_URL || DEFAULT_MCP_URL;
