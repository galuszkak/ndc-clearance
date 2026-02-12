<script lang="ts">
    import type { SearchResult } from "../utils/types";

    let {
        query = $bindable(""),
        searchResults = [],
        isSearching = false,
        searchInput = $bindable<HTMLInputElement | undefined>(undefined),
        onSelectResult,
    }: {
        query: string;
        searchResults: SearchResult[];
        isSearching: boolean;
        searchInput?: HTMLInputElement;
        onSelectResult: (result: SearchResult) => void;
    } = $props();
</script>

<div class="relative flex-1">
    <input
        bind:this={searchInput}
        type="text"
        placeholder="Search elements..."
        class="input input-sm input-bordered w-full pr-16 bg-base-100"
        bind:value={query}
        role="combobox"
        aria-expanded={searchResults.length > 0}
        aria-controls="search-results-listbox"
        aria-autocomplete="list"
    />
    <div
        class="absolute inset-y-0 right-10 flex items-center pointer-events-none"
    >
        <kbd
            class="kbd kbd-xs bg-base-200 border-base-300 opacity-60 font-sans px-1"
        >
            {typeof window !== "undefined" &&
            /mac/i.test(navigator.userAgentData?.platform ?? navigator.platform)
                ? "⌘F"
                : "Ctrl+F"}
        </kbd>
    </div>
    <div
        class="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none text-base-content/40"
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
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            /></svg
        >
    </div>

    <!-- Search Results Dropdown -->
    {#if searchResults.length > 0}
        <div
            id="search-results-listbox"
            role="listbox"
            aria-label="Search results"
            class="absolute top-full left-0 right-0 bg-base-100 shadow-xl border border-base-200 rounded-b-lg max-h-[50vh] overflow-y-auto mt-1 z-50"
        >
            <ul class="menu menu-compact p-0">
                {#each searchResults as result (result.path)}
                    <li role="option" aria-selected="false">
                        <button
                            class="flex flex-col items-start gap-1 py-2 px-3 border-b border-base-100 last:border-0 hover:bg-base-200 transition-colors w-full"
                            onclick={() => onSelectResult(result)}
                        >
                            <div
                                class="flex items-center gap-2 w-full text-left"
                            >
                                <span
                                    class="flex-none opacity-80"
                                    title={result.typeName
                                        ? `Type: ${result.typeName}`
                                        : "Element"}
                                >
                                    {#if result.iconType === "message"}
                                        <svg
                                            xmlns="http://www.w3.org/2000/svg"
                                            class="h-4 w-4 text-purple-600"
                                            viewBox="0 0 20 20"
                                            fill="currentColor"
                                        >
                                            <path
                                                d="M7 3a1 1 0 000 2h6a1 1 0 100-2H7zM4 7a1 1 0 011-1h10a1 1 0 110 2H5a1 1 0 01-1-1zM2 11a2 2 0 012-2h12a2 2 0 012 2v4a2 2 0 01-2 2H4a2 2 0 01-2-2v-4z"
                                            />
                                        </svg>
                                    {:else if result.iconType === "typed"}
                                        <svg
                                            xmlns="http://www.w3.org/2000/svg"
                                            class="h-4 w-4 text-blue-500"
                                            viewBox="0 0 20 20"
                                            fill="currentColor"
                                        >
                                            <path
                                                fill-rule="evenodd"
                                                d="M2.5 3A1.5 1.5 0 001 4.5v.793c.026.009.051.02.076.032L7.674 8.51c.206.1.446.1.652 0l6.598-3.185A.755.755 0 0115 5.293V4.5A1.5 1.5 0 0013.5 3h-11zM15 6.913l-6.598 3.185a.755.755 0 01-.804 0L1 6.914V14.5a1.5 1.5 0 001.5 1.5h11a1.5 1.5 0 001.5-1.5V6.913z"
                                                clip-rule="evenodd"
                                            />
                                        </svg>
                                    {:else}
                                        <svg
                                            xmlns="http://www.w3.org/2000/svg"
                                            class="h-4 w-4 text-orange-500"
                                            viewBox="0 0 20 20"
                                            fill="currentColor"
                                        >
                                            <path
                                                fill-rule="evenodd"
                                                d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
                                                clip-rule="evenodd"
                                            />
                                        </svg>
                                    {/if}
                                </span>

                                <span
                                    class="font-medium text-sm text-base-content/90 truncate {result.matchType ===
                                    'name'
                                        ? 'bg-yellow-200 text-yellow-900 rounded px-1 -mx-1 ring-1 ring-yellow-400/50'
                                        : ''} {result.matchType ===
                                    'doc'
                                        ? 'bg-info/20 text-info-content rounded px-1 -mx-1 ring-1 ring-info/30'
                                        : ''}"
                                >
                                    {result.name}
                                </span>

                                {#if result.typeName}
                                    <span
                                        class="text-[10px] text-base-content/40 font-mono bg-base-200 px-1 rounded ml-1 hidden sm:inline-block"
                                    >
                                        {result.typeName}
                                    </span>
                                {/if}
                            </div>

                            <span
                                class="text-[10px] opacity-40 font-mono truncate w-full text-left pl-6"
                                title={result.path}
                                >{result.path}</span
                            >

                            {#if result.doc}
                                <div
                                    class="text-[10px] text-base-content/60 w-full text-left line-clamp-1 italic pl-6"
                                >
                                    {result.doc}
                                </div>
                            {/if}
                        </button>
                    </li>
                {/each}
            </ul>
        </div>
    {/if}

    <!-- Status region for screen readers -->
    <div class="sr-only" aria-live="polite" role="status">
        {#if isSearching}
            Searching...
        {:else if searchResults.length > 0}
            {searchResults.length} results found
        {/if}
    </div>
</div>
