import { writable } from "svelte/store";
import type { SchemaStats } from "./types";

export const schemaStats = writable<SchemaStats>({
    elements: 0,
    complexTypes: 0,
    simpleTypes: 0,
});
