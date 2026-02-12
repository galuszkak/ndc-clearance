import path from "node:path";

/** Absolute path to the public/schemas directory (build-time only). */
export const SCHEMAS_DIR = path.join(process.cwd(), "public/schemas");
