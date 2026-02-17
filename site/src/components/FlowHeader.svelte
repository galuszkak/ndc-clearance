<script lang="ts">
    import { fade } from "svelte/transition";

    let {
        title,
        description,
        sourceUrl,
    }: {
        title: string;
        description: string;
        sourceUrl?: string | null;
    } = $props();

    let expanded = $state(false);
    let isLong = $derived(description.length > 200);
    let displayDescription = $derived(
        expanded || !isLong ? description : description.slice(0, 200) + "...",
    );
</script>

<div class="bg-base-100 border-b border-base-200 p-6">
    <div class="flex items-start justify-between gap-4">
        <div class="flex-1">
            <h1 class="text-2xl font-bold mb-2">
                {title}
            </h1>

            <div class="text-base-content/70 text-sm max-w-3xl leading-relaxed">
                <p>
                    {displayDescription}
                    {#if isLong}
                        <button
                            class="text-primary hover:underline ml-1 font-medium select-none"
                            onclick={() => (expanded = !expanded)}
                        >
                            {expanded ? "Read less" : "Read more"}
                        </button>
                    {/if}
                </p>
                {#if sourceUrl}
                    <p class="mt-2 text-xs">
                        <a
                            href={sourceUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            class="link link-primary opacity-70 hover:opacity-100"
                        >
                            View original source on IATA
                        </a>
                    </p>
                {/if}
            </div>
        </div>
    </div>
</div>
