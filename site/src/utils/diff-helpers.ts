import type { DiffItem, MessageDiff } from "./types";

/**
 * Static diff files are precomputed for ascending version pairs only
 * (diff_{lo}_to_{hi}.json.gz). Returns the file URL plus whether the user's
 * selection is reversed relative to the stored direction.
 */
export function diffFileUrl(
    from: string,
    to: string,
): { url: string; reversed: boolean } {
    const reversed = from > to;
    const [lo, hi] = reversed ? [to, from] : [from, to];
    return { url: `/diffs/diff_${lo}_to_${hi}.json.gz`, reversed };
}

/** Fetch a gzipped diff file and inflate it with the native DecompressionStream. */
export async function fetchGzippedJson(url: string): Promise<unknown> {
    const res = await fetch(url);
    if (!res.ok) {
        throw new Error(`Failed to load ${url}: ${res.status}`);
    }
    const buffer = await res.arrayBuffer();
    const bytes = new Uint8Array(buffer);
    // Some CDNs set Content-Encoding on .gz files, so the body may arrive
    // already inflated — check the gzip magic number before decompressing.
    if (bytes[0] === 0x1f && bytes[1] === 0x8b) {
        const inflated = new Response(buffer).body!.pipeThrough(
            new DecompressionStream("gzip"),
        );
        return await new Response(inflated).json();
    }
    return JSON.parse(new TextDecoder().decode(bytes));
}

const INVERTED_ITEM_TYPE: Record<DiffItem["type"], DiffItem["type"]> = {
    ADDED: "REMOVED",
    REMOVED: "ADDED",
    MODIFIED: "MODIFIED",
    DOC_CHANGED: "DOC_CHANGED",
};

const INVERTED_ITEM_DESCRIPTION: Record<string, string> = {
    "Element added": "Element removed",
    "Element removed": "Element added",
};

const INVERTED_STATUS: Record<MessageDiff["status"], MessageDiff["status"]> = {
    ADDED: "REMOVED",
    REMOVED: "ADDED",
    CHANGED: "CHANGED",
    UNCHANGED: "UNCHANGED",
};

/** Invert a stored lo→hi diff so it reads as hi→lo. */
export function invertDiff(diffs: MessageDiff[]): MessageDiff[] {
    return diffs.map((msg) => ({
        ...msg,
        status: INVERTED_STATUS[msg.status],
        differences: msg.differences.map((diff) => ({
            ...diff,
            type: INVERTED_ITEM_TYPE[diff.type],
            description:
                INVERTED_ITEM_DESCRIPTION[diff.description] ?? diff.description,
            oldValue: diff.newValue,
            newValue: diff.oldValue,
        })),
    }));
}
