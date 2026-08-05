import { gzipSync } from "node:zlib";
import { afterEach, describe, expect, it, vi } from "vitest";
import { diffFileUrl, fetchGzippedJson, invertDiff } from "./diff-helpers";
import type { MessageDiff } from "./types";

describe("diffFileUrl", () => {
    it("builds ascending URL for ascending selection", () => {
        expect(diffFileUrl("21.3.5", "24.3")).toEqual({
            url: "/diffs/diff_21.3.5_to_24.3.json.gz",
            reversed: false,
        });
    });

    it("flips to ascending URL and marks reversed", () => {
        expect(diffFileUrl("24.3", "21.3.5")).toEqual({
            url: "/diffs/diff_21.3.5_to_24.3.json.gz",
            reversed: true,
        });
    });
});

describe("fetchGzippedJson", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    function stubFetch(body: BodyInit, ok = true, status = 200) {
        vi.stubGlobal(
            "fetch",
            vi.fn(
                async () => new Response(body, { status: ok ? 200 : status }),
            ),
        );
    }

    it("inflates gzipped JSON", async () => {
        stubFetch(new Uint8Array(gzipSync(JSON.stringify([{ a: 1 }]))));
        expect(await fetchGzippedJson("/diffs/x.json.gz")).toEqual([{ a: 1 }]);
    });

    it("parses plain JSON when the CDN already inflated the body", async () => {
        stubFetch(JSON.stringify([{ a: 2 }]));
        expect(await fetchGzippedJson("/diffs/x.json.gz")).toEqual([{ a: 2 }]);
    });

    it("throws on HTTP errors", async () => {
        stubFetch("nope", false, 404);
        await expect(fetchGzippedJson("/diffs/x.json.gz")).rejects.toThrow(
            "404",
        );
    });
});

describe("invertDiff", () => {
    const stored: MessageDiff[] = [
        { messageName: "NewMsg", differences: [], status: "ADDED" },
        { messageName: "GoneMsg", differences: [], status: "REMOVED" },
        {
            messageName: "ChangedMsg",
            status: "CHANGED",
            differences: [
                {
                    path: "Root/New",
                    type: "ADDED",
                    description: "Element added",
                    newValue: "doc",
                },
                {
                    path: "Root/Old",
                    type: "REMOVED",
                    description: "Element removed",
                    oldValue: "doc",
                },
                {
                    path: "Root/Typed",
                    type: "MODIFIED",
                    description: "Type changed",
                    oldValue: "xs:string",
                    newValue: "xs:integer",
                },
                {
                    path: "Root/Docd",
                    type: "DOC_CHANGED",
                    description: "Documentation changed",
                    oldValue: "before",
                    newValue: "after",
                },
            ],
        },
    ];

    it("swaps message and item ADDED/REMOVED and old/new values", () => {
        const inverted = invertDiff(stored);

        expect(inverted[0].status).toBe("REMOVED");
        expect(inverted[1].status).toBe("ADDED");
        expect(inverted[2].status).toBe("CHANGED");

        const [added, removed, modified, doc] = inverted[2].differences;
        expect(added.type).toBe("REMOVED");
        expect(added.description).toBe("Element removed");
        expect(added.oldValue).toBe("doc");
        expect(added.newValue).toBeUndefined();

        expect(removed.type).toBe("ADDED");
        expect(removed.description).toBe("Element added");
        expect(removed.newValue).toBe("doc");

        expect(modified.type).toBe("MODIFIED");
        expect(modified.oldValue).toBe("xs:integer");
        expect(modified.newValue).toBe("xs:string");

        expect(doc.type).toBe("DOC_CHANGED");
        expect(doc.oldValue).toBe("after");
        expect(doc.newValue).toBe("before");
    });

    it("does not mutate the input", () => {
        const before = JSON.stringify(stored);
        invertDiff(stored);
        expect(JSON.stringify(stored)).toBe(before);
    });
});
