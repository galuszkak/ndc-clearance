<script lang="ts">
    import type { LoadedSchemaFile } from "../utils/types";

    let {
        loadedFiles = [],
        selectedFileIndex = 0,
        rawXml = "",
        highlightedXml = "",
        loading = false,
        onSelectFile,
    }: {
        loadedFiles: LoadedSchemaFile[];
        selectedFileIndex: number;
        rawXml: string;
        highlightedXml: string;
        loading: boolean;
        onSelectFile: (index: number) => void;
    } = $props();

    let copyLabel = $state("Copy XML");

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
            <div
                class="flex justify-center p-10 h-full items-center"
            >
                <span
                    class="loading loading-spinner text-primary"
                ></span>
            </div>
        {:else}
            {#if highlightedXml}
                <pre
                    class="m-0 p-4 font-mono text-sm leading-relaxed"><code
                        class="language-xml"
                        >{@html highlightedXml}</code
                    ></pre>
            {:else}
                <div
                    class="flex justify-center p-10 h-full items-center text-base-content/50"
                >
                    <span
                        class="loading loading-spinner loading-sm mr-2"
                    ></span> Highlighting...
                </div>
            {/if}

            <button
                class="btn btn-xs btn-circle btn-ghost absolute top-2 right-2 opacity-50 hover:opacity-100 transition-opacity bg-base-200 text-base-content"
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
