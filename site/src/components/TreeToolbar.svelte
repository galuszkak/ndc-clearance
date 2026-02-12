<script lang="ts">
    import { ZOOM } from "../utils/constants";

    let {
        zoom = $bindable(ZOOM.DEFAULT),
        onExpandAll,
        onCollapseAll,
    }: {
        zoom: number;
        onExpandAll: () => void;
        onCollapseAll: () => void;
    } = $props();
</script>

<div class="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 group/toolbar">
    <div class="absolute -inset-4 -bottom-10"></div>
    <div
        class="flex items-center gap-2 p-1.5 bg-base-100/90 backdrop-blur shadow-xl border border-base-200 rounded-full opacity-0 group-hover/toolbar:opacity-100 transition-opacity duration-300"
    >
        <!-- Zoom Controls -->
        <div
            class="join join-horizontal shadow-sm border border-base-200 rounded-full"
        >
            <button
                class="btn btn-xs btn-ghost join-item px-2"
                onclick={() =>
                    (zoom = Math.max(ZOOM.MIN, zoom - ZOOM.STEP))}
                title="Zoom Out"
                aria-label="Zoom out"
            >
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    class="h-3 w-3"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    ><path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M20 12H4"
                    /></svg
                >
            </button>
            <div
                class="join-item px-2 flex items-center bg-base-100 text-[10px] font-mono font-bold w-10 justify-center select-none"
                aria-live="polite"
            >
                {zoom}%
            </div>
            <button
                class="btn btn-xs btn-ghost join-item px-2"
                onclick={() =>
                    (zoom = Math.min(ZOOM.MAX, zoom + ZOOM.STEP))}
                title="Zoom In"
                aria-label="Zoom in"
            >
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    class="h-3 w-3"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    ><path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M12 4v16m8-8H4"
                    /></svg
                >
            </button>
        </div>

        <div class="w-px h-4 bg-base-300 mx-1"></div>

        <!-- Tree Controls -->
        <div
            class="join join-horizontal shadow-sm border border-base-200 rounded-full bg-base-100"
        >
            <button
                class="btn btn-xs btn-ghost join-item rounded-l-full normal-case font-normal px-3"
                onclick={onExpandAll}
                aria-label="Expand all tree nodes"
            >
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    class="h-3 w-3 mr-1 opacity-60"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    ><path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M19 13l-7 7-7-7m14-8l-7 7-7-7"
                    /></svg
                >
                Expand All
            </button>
            <div class="w-px bg-base-200 h-full"></div>
            <button
                class="btn btn-xs btn-ghost join-item rounded-r-full normal-case font-normal px-3"
                onclick={onCollapseAll}
                aria-label="Collapse all tree nodes"
            >
                <svg
                    xmlns="http://www.w3.org/2000/svg"
                    class="h-3 w-3 mr-1 opacity-60"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    ><path
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="2"
                        d="M5 11l7-7 7 7M5 19l7-7 7 7"
                    /></svg
                >
                Collapse All
            </button>
        </div>
    </div>
</div>
