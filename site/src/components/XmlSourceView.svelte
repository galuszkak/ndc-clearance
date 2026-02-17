<script lang="ts">
    import type { LoadedSchemaFile } from "../utils/types";

    let {
        loadedFiles = [],
        selectedFileIndex = 0,
        rawXml = "",
        highlightedXml = "",
        loading = false,
        onSelectFile = () => {}, // Default empty function
    }: {
        loadedFiles?: LoadedSchemaFile[]; // Optional
        selectedFileIndex?: number; // Optional
        rawXml: string;
        highlightedXml: string;
        loading: boolean;
        onSelectFile?: (index: number) => void; // Optional
    } = $props();

    let copyLabel = $state("Copy XML");

    // Folding state
    let collapsedLines = $state(new Set<number>());

    // Derived state for rendered lines
    let renderedLines = $derived.by(() => {
        if (!highlightedXml) return [];

        const lines = highlightedXml.split(/\r?\n/);
        const result: {
            html: string;
            indent: number;
            canFold: boolean;
            lineNumber: number;
        }[] = [];

        let openSpans: string[] = [];

        for (let i = 0; i < lines.length; i++) {
            let line = lines[i];

            // 1. Prepend open spans from previous line
            let lineHtml = openSpans.join("") + line;

            // 2. Calculate indentation (using raw text if possible, but html might have tags)
            // We use a regex to strip tags for indent calculation, or just check the rawXml lines?
            // Checking rawXml is safer for indentation.
            // But we need to make sure rawXml aligns 1:1 with highlightedXml lines.
            // Usually highlight.js preserves lines.

            // 3. Update open spans for next line
            // Find all tags
            const tags = line.match(/<\/?span[^>]*>/g) || [];
            for (const tag of tags) {
                if (tag.startsWith("</")) {
                    openSpans.pop();
                } else if (!tag.includes("/>")) {
                    // Ignore self-closing if any (unlikely for span)
                    openSpans.push(tag);
                }
            }

            // 4. Close all open spans at end of this line
            lineHtml += "</span>".repeat(openSpans.length);

            result.push({
                html: lineHtml,
                indent: 0, // Placeholder, will fill below
                canFold: false,
                lineNumber: i,
            });
        }

        // Calculate indentation and foldability from RAW XML to be accurate
        const rawLines = rawXml.split(/\r?\n/);
        if (rawLines.length === result.length) {
            const indents = rawLines.map((l) => l.search(/\S|$/));

            for (let i = 0; i < result.length; i++) {
                result[i].indent = indents[i];

                // Determine if foldable: strictly indent-based
                // A line is foldable if the *next* line is more indented
                if (i < result.length - 1 && indents[i + 1] > indents[i]) {
                    result[i].canFold = true;
                }
            }
        }

        return result;
    });

    function toggleFold(lineNumber: number) {
        const newSet = new Set(collapsedLines);
        if (newSet.has(lineNumber)) {
            newSet.delete(lineNumber);
        } else {
            newSet.add(lineNumber);
        }
        collapsedLines = newSet;
    }

    function isHidden(
        lineNumber: number,
        lines: typeof renderedLines,
    ): boolean {
        // A line is hidden if any of its parents are collapsed
        // We need to find the "parent" of this line.
        // A parent is the closest preceding line with smaller indentation.

        // Optimization: checking every line's parents on render is O(N^2) worst case.
        // Better: iterate linearly when rendering?
        // But here we are inside the #each loop logic or derived?

        // Actually, the simplest way for the template is:
        // if a line is collapsed, hide all subsequent lines until indent <= collapsed indent.
        return false; // Handled in the template logic for efficiency?
    }

    // We need a derived "visible" line set or just logic in the template?
    // Doing O(N) pass to determine visibility is best.
    let visibleLines = $derived.by(() => {
        const visible = new Set<number>();
        let hideUntilDepth = Infinity;

        for (let i = 0; i < renderedLines.length; i++) {
            const line = renderedLines[i];

            if (line.indent <= hideUntilDepth) {
                // We are back out of the collapsed block
                hideUntilDepth = Infinity;
            }

            if (hideUntilDepth === Infinity) {
                visible.add(i);

                if (collapsedLines.has(i) && line.canFold) {
                    // Start hiding children
                    // We hide everything with indent > current indent
                    // So we hide until we see indent <= current indent
                    hideUntilDepth = line.indent;
                }
            }
        }
        return visible;
    });

    async function handleCopy() {
        try {
            await navigator.clipboard.writeText(rawXml);
            copyLabel = "Copied!";
            setTimeout(() => (copyLabel = "Copy XML"), 2000);
        } catch {
            copyLabel = "Copy failed";
            setTimeout(() => (copyLabel = "Copy XML"), 2000);
        }
    }
</script>

<div
    class="flex h-full flex-col md:flex-row bg-base-100 rounded-lg shadow border border-base-200 overflow-hidden"
>
    {#if loadedFiles.length > 1}
        <div
            class="w-full md:w-48 border-b md:border-b-0 md:border-r border-base-200 bg-base-50 overflow-y-auto max-h-40 md:max-h-full flex-none"
            role="tablist"
            aria-label="Schema files"
        >
            <div
                class="p-2 text-xs font-bold opacity-50 uppercase tracking-wider sticky top-0 bg-base-50 z-10"
            >
                Files
            </div>
            <div class="flex flex-col">
                {#each loadedFiles as file, idx (file.name)}
                    <button
                        role="tab"
                        aria-selected={selectedFileIndex === idx}
                        class="text-left px-3 py-2 text-xs font-mono truncate hover:bg-base-200 transition-colors {selectedFileIndex ===
                        idx
                            ? 'bg-primary/10 text-primary border-r-2 border-primary'
                            : ''}"
                        onclick={() => onSelectFile(idx)}
                        title={file.name}
                    >
                        {file.name}
                    </button>
                {/each}
            </div>
        </div>
    {/if}

    <div
        class="flex-1 overflow-auto relative bg-[#0d1117] text-gray-300 pointer-events-auto select-text min-w-0"
        role="tabpanel"
    >
        {#if loading}
            <div class="flex justify-center p-10 h-full items-center">
                <span class="loading loading-spinner text-primary"></span>
            </div>
        {:else}
            {#if renderedLines.length > 0}
                <div class="font-mono text-sm leading-6 p-4 min-w-max">
                    {#each renderedLines as line (line.lineNumber)}
                        {#if visibleLines.has(line.lineNumber)}
                            <div class="flex group">
                                <!-- Gutter / Fold Button -->
                                <div
                                    class="w-6 flex-none select-none text-center opacity-50 relative"
                                >
                                    {#if line.canFold}
                                        <button
                                            class="absolute inset-0 flex items-center justify-center hover:text-white cursor-pointer"
                                            onclick={() =>
                                                toggleFold(line.lineNumber)}
                                            aria-label={collapsedLines.has(
                                                line.lineNumber,
                                            )
                                                ? "Expand"
                                                : "Collapse"}
                                        >
                                            {#if collapsedLines.has(line.lineNumber)}
                                                <svg
                                                    xmlns="http://www.w3.org/2000/svg"
                                                    viewBox="0 0 16 16"
                                                    fill="currentColor"
                                                    class="w-3 h-3"
                                                >
                                                    <path
                                                        d="M6.75 3.25a.75.75 0 0 1 1.5 0v3.5h3.5a.75.75 0 0 1 0 1.5h-3.5v3.5a.75.75 0 0 1-1.5 0v-3.5h-3.5a.75.75 0 0 1 0-1.5h3.5v-3.5Z"
                                                    />
                                                </svg>
                                            {:else}
                                                <svg
                                                    xmlns="http://www.w3.org/2000/svg"
                                                    viewBox="0 0 16 16"
                                                    fill="currentColor"
                                                    class="w-3 h-3 opacity-0 group-hover:opacity-100 transition-opacity"
                                                >
                                                    <path
                                                        d="M4.25 7.25a.75.75 0 0 0 0 1.5h7.5a.75.75 0 0 0 0-1.5h-7.5Z"
                                                    />
                                                </svg>
                                            {/if}
                                        </button>
                                    {/if}
                                </div>

                                <!-- Code Content -->
                                <div class="whitespace-pre">
                                    {@html line.html}{#if collapsedLines.has(line.lineNumber)}<span
                                            class="select-none opacity-50 text-xs ml-2"
                                            >...</span
                                        >{/if}
                                </div>
                            </div>
                        {/if}
                    {/each}
                </div>
            {:else if !highlightedXml}
                <div
                    class="flex justify-center p-10 h-full items-center text-base-content/50"
                >
                    <span class="loading loading-spinner loading-sm mr-2"
                    ></span> Highlighting...
                </div>
            {:else}
                <!-- Empty file fallback -->
                <div class="p-4 text-gray-500 font-mono text-sm">
                    (Empty file)
                </div>
            {/if}

            <button
                class="btn btn-xs btn-circle btn-ghost absolute top-2 right-2 opacity-50 hover:opacity-100 transition-opacity bg-base-200 text-base-content z-20"
                title={copyLabel}
                aria-label="Copy XML to clipboard"
                onclick={handleCopy}
            >
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    class="h-4 w-4"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    ><path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"
                    /></svg
                >
            </button>
        {/if}
    </div>
</div>
