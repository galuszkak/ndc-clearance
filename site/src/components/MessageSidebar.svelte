<script lang="ts">
    import VersionSelector from "./VersionSelector.svelte";

    export let messages: string[] = [];
    export let currentMessage: string = "";
    export let version: string = "";
    export let allVersions: string[] = [];

    let query = "";
    $: filteredMessages = query
        ? messages.filter((m) => m.toLowerCase().includes(query.toLowerCase()))
        : messages;

    function getShortName(name: string) {
        return name.replace("IATA_", "");
    }

    let filterInput: HTMLInputElement;

    function handleKeyDown(e: KeyboardEvent) {
        if (
            e.key === "/" &&
            document.activeElement?.tagName !== "INPUT" &&
            document.activeElement?.tagName !== "TEXTAREA"
        ) {
            e.preventDefault();
            if (filterInput) {
                filterInput.focus();
                filterInput.select();
            }
        }
    }
</script>

<svelte:window on:keydown={handleKeyDown} />

<div class="flex flex-col h-full overflow-hidden">
    <div
        class="px-2 mb-2 sticky top-0 bg-base-100 z-10 py-2 border-b border-base-200/50"
    >
        <div class="mb-2">
            <VersionSelector
                currentVersion={version}
                {currentMessage}
                {allVersions}
            />
        </div>
        <div class="relative">
            <input
                bind:this={filterInput}
                type="text"
                placeholder="Filter messages..."
                class="input input-sm input-bordered w-full pr-10 pl-8 text-xs h-8"
                bind:value={query}
            />
            <div
                class="absolute inset-y-0 right-8 flex items-center pointer-events-none opacity-40"
            >
                <kbd class="kbd kbd-xs h-4 min-h-0 px-1 font-sans">/</kbd>
            </div>
            <div
                class="absolute inset-y-0 left-3 flex items-center pointer-events-none text-base-content/40"
            >
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    class="h-3 w-3"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                >
                    <path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                    />
                </svg>
            </div>
            {#if query}
                <button
                    class="absolute inset-y-0 right-2 flex items-center text-base-content/40 hover:text-base-content"
                    on:click={() => (query = "")}
                    aria-label="Clear filter"
                >
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-3 w-3"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                    >
                        <path
                            stroke-linecap="round"
                            stroke-linejoin="round"
                            stroke-width="2"
                            d="M6 18L18 6M6 6l12 12"
                        />
                    </svg>
                </button>
            {/if}
        </div>
    </div>

    <ul class="menu menu-sm p-0 overflow-y-auto flex-1 flex-nowrap w-full">
        {#each filteredMessages as msg}
            <li>
                <a
                    href={`/${version}/${msg}`}
                    class="flex items-center gap-2 px-3 py-2 transition-all duration-200 hover:bg-base-200 whitespace-nowrap overflow-hidden {msg ===
                    currentMessage
                        ? 'active bg-primary/10 text-primary font-semibold border-r-2 border-primary'
                        : 'text-base-content/70 hover:text-base-content'}"
                    title={msg}
                >
                    <span class="flex-none opacity-40">
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
                                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                            />
                        </svg>
                    </span>
                    <span class="truncate text-xs min-w-0">
                        {getShortName(msg)}
                    </span>
                </a>
            </li>
        {/each}
        {#if filteredMessages.length === 0}
            <li class="px-4 py-8 text-center opacity-40 text-xs italic">
                No messages match "{query}"
            </li>
        {/if}
    </ul>
</div>

<style>
    .menu :where(li:not(.menu-title) > *:not(ul):not(details):not(.menu-title)),
    .menu :where(li:not(.menu-title) > details > summary:not(.menu-title)) {
        border-radius: 0.375rem;
    }

    .menu {
        content-visibility: auto;
        contain-intrinsic-size: 1px 1000px;
    }
</style>
