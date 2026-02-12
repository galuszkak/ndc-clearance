<script lang="ts">
    import type { SelectedNodeHelper } from "../utils/types";

    let {
        selectedNode = null,
        selectedHelper = {},
    }: {
        selectedNode: Element | null;
        selectedHelper: SelectedNodeHelper;
    } = $props();
</script>

<div
    class="h-56 lg:h-auto w-full lg:w-80 bg-base-100 rounded-lg shadow border border-base-200 flex flex-col overflow-hidden flex-none"
>
    <div
        class="p-3 border-b border-base-200 bg-base-50 font-bold text-xs uppercase tracking-wider"
    >
        Node Properties
    </div>
    <div class="p-4 flex-1 overflow-y-auto">
        {#if selectedNode}
            <div class="flex flex-col gap-6">
                <!-- Header -->
                <div>
                    <div class="flex gap-2 mb-1">
                        {#if selectedNode.localName === "choice"}
                            <span
                                class="badge badge-accent badge-sm uppercase"
                                >CHOICE</span
                            >
                        {:else if (selectedNode.getAttribute("name") || "").endsWith("RS") || (selectedNode.getAttribute("name") || "").endsWith("RQ")}
                            <span
                                class="badge badge-secondary badge-sm uppercase"
                                >MESSAGE</span
                            >
                        {:else}
                            <span class="badge badge-primary badge-sm"
                                >ELEMENT</span
                            >
                        {/if}
                        {#if selectedHelper.min !== "0"}
                            <span class="badge badge-primary badge-sm"
                                >REQUIRED</span
                            >
                        {:else}
                            <span
                                class="badge badge-ghost badge-sm border-base-300"
                                >OPTIONAL</span
                            >
                        {/if}
                        {#if selectedHelper.max === "unbounded" || parseInt(selectedHelper.max || "1") > 1}
                            <span class="badge badge-neutral badge-sm"
                                >LIST</span
                            >
                        {/if}
                    </div>
                    <h2 class="text-lg font-bold break-words">
                        {selectedNode.getAttribute("name") ||
                            selectedNode.getAttribute("ref") ||
                            "Anonymous"}
                    </h2>
                    <div
                        class="text-xs text-base-content/60 font-mono mt-1"
                    >
                        Type: <span class="text-primary"
                            >{selectedHelper.typeName}</span
                        >
                    </div>
                </div>

                <!-- Description -->
                <div class="prose prose-sm">
                    <h3
                        class="text-xs font-bold uppercase text-base-content/50 mb-2"
                    >
                        Description
                    </h3>
                    <p
                        class="text-sm leading-relaxed text-base-content/80"
                    >
                        {selectedHelper.doc}
                    </p>
                </div>

                <!-- Constraints -->
                <div
                    class="p-3 bg-base-200/50 rounded-lg border border-base-200"
                >
                    <h3
                        class="text-xs font-bold uppercase text-base-content/50 mb-3 flex items-center gap-2"
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
                                d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
                            /><path
                                stroke-linecap="round"
                                stroke-linejoin="round"
                                stroke-width="2"
                                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                            /></svg
                        >
                        Technical Constraints
                    </h3>
                    <div class="grid grid-cols-2 gap-4 text-xs">
                        <div>
                            <div class="opacity-50 mb-1">
                                OCCURRENCES
                            </div>
                            <div class="font-mono font-bold">
                                {selectedHelper.min} .. {selectedHelper.max ===
                                "unbounded"
                                    ? "∞"
                                    : selectedHelper.max}
                            </div>
                        </div>
                        <div>
                            <div class="opacity-50 mb-1">BASE TYPE</div>
                            <div class="font-mono truncate">
                                {selectedHelper.typeName || "N/A"}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        {:else}
            <div class="text-center mt-10 text-base-content/40">
                <p class="text-sm">
                    Select an element to view details.
                </p>
            </div>
        {/if}
    </div>
</div>
