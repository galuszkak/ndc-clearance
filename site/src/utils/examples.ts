import fs from "node:fs";
import path from "node:path";
import type { ExampleCatalog, ExampleRecord } from "./types";

const EMPTY_CATALOG: ExampleCatalog = {
    version: 1,
    generated_at: "",
    examples: [],
};

export function loadExampleCatalog(cwd: string = process.cwd()): ExampleCatalog {
    const catalogPath = path.join(cwd, "public", "content", "examples", "catalog.json");
    try {
        if (!fs.existsSync(catalogPath)) {
            return EMPTY_CATALOG;
        }

        const raw = fs.readFileSync(catalogPath, "utf-8");
        const parsed = JSON.parse(raw) as ExampleCatalog;
        if (!Array.isArray(parsed.examples)) {
            return EMPTY_CATALOG;
        }
        return parsed;
    } catch {
        return EMPTY_CATALOG;
    }
}

export function getExamplesForMessage(
    message: string,
    catalog: ExampleCatalog,
    version?: string,
): ExampleRecord[] {
    const normalizedMessage = message.trim();
    if (!normalizedMessage) return [];

    const messageWithoutPrefix = normalizedMessage.startsWith("IATA_")
        ? normalizedMessage.slice(5)
        : normalizedMessage;
    const messageWithPrefix = normalizedMessage.startsWith("IATA_")
        ? normalizedMessage
        : `IATA_${normalizedMessage}`;
    const normalizedVersion = version?.trim() || undefined;

    const candidates = new Set<string>([
        normalizedMessage,
        messageWithoutPrefix,
        messageWithPrefix,
    ]);

    return catalog.examples.filter((example) => {
        if (!example.is_active) return false;
        if (!candidates.has(example.message)) return false;
        if (normalizedVersion && example.version !== normalizedVersion) return false;
        return true;
    });
}
