import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import type { FlowRecord } from "./types";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Helper to find project root (where ndc_content lives)
// In dev: site/src/utils/flows.ts -> ../../../ndc_content
// In build: depends, but we can rely on relative paths or process.cwd()
function getProjectRoot() {
    // Basic heuristic: look for ndc_content in parent dirs
    let current = __dirname;
    while (current !== "/") {
        if (fs.existsSync(path.join(current, "ndc_content"))) {
            return current;
        }
        current = path.dirname(current);
    }
    // Fallback to process.cwd() which usually is the project root in Astro
    return process.cwd();
}

const FLOWS_PATH = path.join(
    getProjectRoot(),
    "ndc_content/flows/flows.json",
);

export function getFlows(): FlowRecord[] {
    if (!fs.existsSync(FLOWS_PATH)) {
        console.warn(`Flows file not found at ${FLOWS_PATH}`);
        // Fallback to try finding it relative to site/ if running from site root
        const fallbackPath = path.resolve("..", "ndc_content/flows/flows.json");
        if (fs.existsSync(fallbackPath)) {
            const data = fs.readFileSync(fallbackPath, "utf-8");
            return JSON.parse(data).flows;
        }
        return [];
    }
    const data = fs.readFileSync(FLOWS_PATH, "utf-8");
    const parsed = JSON.parse(data);
    return (parsed.flows || []).filter(
        (f: FlowRecord) =>
            f.steps &&
            f.steps.length > 0 &&
            f.steps.some((s) => s.example_id && s.example_id.trim() !== ""),
    );
}

export function getFlowById(id: string): FlowRecord | undefined {
    const flows = getFlows();
    return flows.find((f) => f.id === id);
}

export function getFlowsForMessage(
    message: string,
    flows: FlowRecord[],
): FlowRecord[] {
    const normalized = message.trim();
    if (!normalized) return [];

    const withoutPrefix = normalized.startsWith("IATA_")
        ? normalized.slice(5)
        : normalized;
    const withPrefix = normalized.startsWith("IATA_")
        ? normalized
        : `IATA_${normalized}`;

    const candidates = new Set<string>([
        normalized,
        withoutPrefix,
        withPrefix,
    ]);

    return flows.filter((flow) =>
        flow.steps.some((step) => candidates.has(step.message)),
    );
}
