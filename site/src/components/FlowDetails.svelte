<script lang="ts">
    import type { ExampleRecord, FlowStep } from "../utils/types";
    import { hljs } from "../utils/highlight";
    import XmlSourceView from "./XmlSourceView.svelte"; // Import the new component

    let {
        step,
        example,
    }: {
        step: FlowStep | null;
        example: ExampleRecord | null;
    } = $props();

    let loading = $state(false);
    let xmlContent = $state("");
    let highlightedXml = $state(""); // Add state for highlighted XML
    let error = $state("");

    $effect(() => {
        if (example) {
            loading = true;
            error = "";
            xmlContent = "";
            highlightedXml = "";

            fetch(`${example.public_path}?t=${Date.now()}`)
                .then((res) => {
                    if (!res.ok)
                        throw new Error("Failed to load example content");
                    return res.text();
                })
                .then((text) => {
                    xmlContent = text;
                    try {
                        // Highlight synchronously now that we have the text
                        highlightedXml = hljs.highlight(text, {
                            language: "xml",
                        }).value;
                    } catch (e) {
                        // Fallback or error handling
                        console.warn("Highlight failed", e);
                        highlightedXml = text
                            .replace(/&/g, "&amp;")
                            .replace(/</g, "&lt;")
                            .replace(/>/g, "&gt;");
                    }
                    loading = false;
                })
                .catch((e) => {
                    error = e.message;
                    loading = false;
                });
        } else {
            xmlContent = "";
            highlightedXml = "";
            error = "";
            loading = false;
        }
    });
</script>

<div class="flex flex-col h-full bg-base-100">
    {#if !step}
        <div
            class="flex-1 flex flex-col items-center justify-center text-base-content/30 p-8 text-center"
        >
            <svg
                xmlns="http://www.w3.org/2000/svg"
                class="h-12 w-12 mb-4 opacity-50"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
            >
                <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.5"
                    d="M13 10V3L4 14h7v7l9-11h-7z"
                />
            </svg>
            <p class="text-sm font-medium">Select a step to view details</p>
        </div>
    {:else}
        <div class="p-4 border-b border-base-200 bg-base-50/50 flex-shrink-0">
            <div class="flex items-center justify-between mb-2">
                <span
                    class="badge badge-primary badge-outline font-mono text-xs"
                    >Step {step.order}</span
                >
                {#if step.optional}
                    <span class="badge badge-ghost badge-sm">Optional</span>
                {/if}
            </div>
            <div class="flex items-center justify-between gap-2">
                <h3 class="font-bold text-lg">{step.message}</h3>
                <a
                    href={`/${example?.version || "25.4"}/${step.message.replace(/^IATA_/, "")}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    class="btn btn-secondary btn-xs btn-outline gap-1 flex-none"
                    title="View schema definition"
                >
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-3.5 w-3.5"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            stroke-width="2"
                            d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"
                        />
                    </svg>
                    Schema
                </a>
            </div>
        </div>

        <div class="flex-1 overflow-hidden relative min-h-0 bg-[#0d1117]">
            {#if error}
                <div class="p-4 text-error text-sm">{error}</div>
            {:else if xmlContent || loading}
                <XmlSourceView rawXml={xmlContent} {highlightedXml} {loading} />
            {:else if !loading && !error}
                <div
                    class="flex-1 flex flex-col items-center justify-center text-base-content/30 p-8 text-center h-full bg-base-100"
                >
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-12 w-12 mb-4 opacity-50"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            stroke-width="1.5"
                            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                        />
                    </svg>
                    <p class="text-sm font-medium">
                        No example XML available for this step
                    </p>
                </div>
            {/if}
        </div>
    {/if}
</div>
