import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { getExamplesForMessage, loadExampleCatalog } from "./examples";
import type { ExampleCatalog } from "./types";

const tempDirs: string[] = [];

function mkTempSiteRoot(): string {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "ndc-site-test-"));
    tempDirs.push(dir);
    return dir;
}

function writeCatalog(siteRoot: string, catalog: ExampleCatalog): void {
    const filePath = path.join(siteRoot, "public", "content", "examples", "catalog.json");
    fs.mkdirSync(path.dirname(filePath), { recursive: true });
    fs.writeFileSync(filePath, JSON.stringify(catalog), "utf-8");
}

afterEach(() => {
    for (const dir of tempDirs.splice(0)) {
        fs.rmSync(dir, { recursive: true, force: true });
    }
});

describe("examples utils", () => {
    it("loadExampleCatalog returns empty catalog when file is missing", () => {
        const siteRoot = mkTempSiteRoot();
        const catalog = loadExampleCatalog(siteRoot);
        expect(catalog.examples).toEqual([]);
        expect(catalog.version).toBe(1);
    });

    it("loadExampleCatalog reads canonical catalog file", () => {
        const siteRoot = mkTempSiteRoot();
        writeCatalog(siteRoot, {
            version: 1,
            generated_at: "2026-02-14T00:00:00Z",
            examples: [
                {
                    id: "ex_1",
                    source: "iata",
                    message: "IATA_OrderCreateRQ",
                    version: "24.1",
                    title: "Sample",
                    description: null,
                    tags: [],
                    file_name: "sample.xml",
                    xml_path: "ndc_content/examples/files/iata/ex_1.xml",
                    public_path: "/content/examples/files/iata/ex_1.xml",
                    source_url: null,
                    source_page_id: null,
                    flow_id: null,
                    is_active: true,
                },
            ],
        });

        const catalog = loadExampleCatalog(siteRoot);
        expect(catalog.examples).toHaveLength(1);
        expect(catalog.examples[0].id).toBe("ex_1");
    });

    it("getExamplesForMessage matches prefix variants and filters by version and active flag", () => {
        const catalog: ExampleCatalog = {
            version: 1,
            generated_at: "2026-02-14T00:00:00Z",
            examples: [
                {
                    id: "ex_a",
                    source: "iata",
                    message: "IATA_OrderCreateRQ",
                    version: "24.1",
                    title: "A",
                    description: null,
                    tags: [],
                    file_name: "a.xml",
                    xml_path: "ndc_content/examples/files/iata/ex_a.xml",
                    public_path: "/content/examples/files/iata/ex_a.xml",
                    source_url: null,
                    source_page_id: null,
                    flow_id: null,
                    is_active: true,
                },
                {
                    id: "ex_b",
                    source: "custom",
                    message: "OrderCreateRQ",
                    version: "25.4",
                    title: "B",
                    description: null,
                    tags: [],
                    file_name: "b.xml",
                    xml_path: "ndc_content/examples/files/custom/ex_b.xml",
                    public_path: "/content/examples/files/custom/ex_b.xml",
                    source_url: null,
                    source_page_id: null,
                    flow_id: null,
                    is_active: true,
                },
                {
                    id: "ex_c",
                    source: "iata",
                    message: "IATA_OrderCreateRQ",
                    version: "24.1",
                    title: "C",
                    description: null,
                    tags: [],
                    file_name: "c.xml",
                    xml_path: "ndc_content/examples/files/iata/ex_c.xml",
                    public_path: "/content/examples/files/iata/ex_c.xml",
                    source_url: null,
                    source_page_id: null,
                    flow_id: null,
                    is_active: false,
                },
            ],
        };

        const all = getExamplesForMessage("OrderCreateRQ", catalog);
        expect(all.map((x) => x.id)).toEqual(["ex_a", "ex_b"]);

        const versioned = getExamplesForMessage("IATA_OrderCreateRQ", catalog, "24.1");
        expect(versioned.map((x) => x.id)).toEqual(["ex_a"]);
    });
});
