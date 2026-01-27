<script lang="ts">
    import hljs from "highlight.js/lib/core";
    import xml from "highlight.js/lib/languages/xml";
    import { tick } from "svelte";

    hljs.registerLanguage("xml", xml);

    // Svelte 5 runes
    let { examples = [], message = "", version = "" } = $props();

    $effect(() => {
        console.log("ExamplesModal received examples:", examples);
        examples.forEach((ex) => {
            console.log(`Title: "${ex.title}", URL: "${ex.url}"`);
        });
    });

    let selectedExample = $state(null);
    let xmlContent = $state("");
    let loading = $state(false);
    let codeElement = $state<HTMLElement | null>(null);

    async function previewExample(example: any) {
        selectedExample = example;
        loading = true;
        xmlContent = "";
        try {
            // Add a cache-busting param to avoid 404 if file was just created
            const response = await fetch(`${example.path}?t=${Date.now()}`);
            if (!response.ok) {
                console.error(
                    "Failed to fetch example:",
                    example.path,
                    response.status,
                );
                xmlContent = `Error ${response.status}: Failed to load ${example.path}`;
            } else {
                xmlContent = await response.text();
                // console.log("Loaded XML content length:", xmlContent.length);
            }
        } catch (e: any) {
            console.error("Fetch error:", e);
            xmlContent = "Error loading XML content: " + e.message;
        } finally {
            loading = false;
            // Wait for DOM update then highlight
            await tick();
            if (codeElement) {
                // console.log("Highlighting element...", codeElement);
                try {
                    // Reset highlighting
                    codeElement.removeAttribute("data-highlighted");
                    codeElement.className = "language-xml";
                    hljs.highlightElement(codeElement);
                } catch (err) {
                    console.error("Highlighting error:", err);
                }
            }
        }
    }

    function closePreview() {
        selectedExample = null;
        xmlContent = "";
    }
</script>

<dialog id="examples_modal" class="modal">
    <div class="modal-box w-11/12 max-w-5xl h-[80vh] flex flex-col p-6">
        <div class="flex justify-between items-center mb-6">
            <h3 class="font-bold text-xl flex items-center gap-2">
                {#if selectedExample}
                    <button
                        class="btn btn-ghost btn-sm"
                        onclick={closePreview}
                        aria-label="Back to examples list"
                    >
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            class="h-4 w-4"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                        >
                            <path
                                stroke-linecap="round"
                                stroke-linejoin="round"
                                stroke-width="2"
                                d="M15 19l-7-7 7-7"
                            />
                        </svg>
                    </button>
                    <span class="truncate">{selectedExample.title}</span>
                {:else}
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-6 w-6 text-secondary"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            stroke-width="2"
                            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                        />
                    </svg>
                    Worked Examples for {message}
                {/if}
            </h3>
            <form method="dialog">
                <button class="btn btn-sm btn-circle btn-ghost">✕</button>
            </form>
        </div>

        <div class="flex-1 overflow-auto min-h-0">
            {#if selectedExample}
                {#if loading}
                    <div
                        class="flex flex-col justify-center items-center h-full gap-4"
                    >
                        <span
                            class="loading loading-spinner loading-xl text-primary"
                        ></span>
                        <p class="animate-pulse">Loading example content...</p>
                    </div>
                {:else}
                    <div
                        class="bg-neutral-900 rounded-box border border-base-content/10 shadow-xl overflow-hidden"
                    >
                        <pre class="!bg-transparent overflow-x-auto"><code
                                bind:this={codeElement}
                                class="language-xml text-sm leading-relaxed"
                                >{xmlContent}</code
                            ></pre>
                    </div>
                {/if}
            {:else}
                <div class="grid gap-4">
                    {#each examples as example}
                        <div
                            class="group card bg-base-200 hover:bg-base-300 transition-all duration-300 border border-base-content/5 shadow-sm hover:shadow-md"
                        >
                            <div class="card-body p-5">
                                <div
                                    class="flex justify-between items-start gap-4"
                                >
                                    <div class="flex-1">
                                        <h4
                                            class="font-bold text-lg leading-snug"
                                        >
                                            {example.title}
                                        </h4>
                                        <div
                                            class="flex items-center gap-3 mt-2"
                                        >
                                            <span
                                                class="badge badge-sm badge-secondary badge-outline font-mono"
                                                >v{example.original_version}</span
                                            >
                                            <span
                                                class="text-[10px] opacity-40 font-mono italic truncate max-w-xs"
                                                >{example.file}</span
                                            >
                                        </div>
                                    </div>
                                    <div class="flex flex-col gap-2 shrink-0">
                                        <button
                                            class="btn btn-primary btn-sm px-6 shadow-sm"
                                            onclick={() =>
                                                previewExample(example)}
                                        >
                                            Preview
                                        </button>
                                        {#if example.url}
                                            <a
                                                href={example.url}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                class="btn btn-ghost btn-xs opacity-60 hover:opacity-100 transition-opacity"
                                            >
                                                IATA Site
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
                                            </a>
                                        {/if}
                                    </div>
                                </div>
                            </div>
                        </div>
                    {/each}
                </div>
            {/if}
        </div>

        <div class="modal-action border-t border-base-content/5 pt-4">
            <form method="dialog">
                <button class="btn btn-sm btn-ghost">Close</button>
            </form>
        </div>
    </div>
    <form
        method="dialog"
        class="modal-backdrop bg-black/40 backdrop-blur-[2px]"
    >
        <button>close</button>
    </form>
</dialog>

<style>
    code {
        font-family: "JetBrains Mono", "Fira Code", "Courier New", monospace;
        white-space: pre;
    }
</style>
