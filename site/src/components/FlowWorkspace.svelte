<script lang="ts">
    import type { FlowRecord, ExampleCatalog } from "../utils/types";
    import FlowGraph from "./FlowGraph.svelte";
    import FlowDetails from "./FlowDetails.svelte";

    let {
        flow,
        catalog,
    }: {
        flow: FlowRecord;
        catalog: ExampleCatalog;
    } = $props();

    let selectedStepId = $state<string | null>(null);

    // Reset selection when flow changes
    $effect(() => {
        selectedStepId = flow.steps.length > 0 ? flow.steps[0].step_id : null;
    });

    let selectedStep = $derived(
        selectedStepId
            ? flow.steps.find((s) => s.step_id === selectedStepId) || null
            : null,
    );

    let selectedExample = $derived(
        selectedStep
            ? catalog.examples.find((e) => e.id === selectedStep.example_id) ||
                  null
            : null,
    );

    function handleNodeClick(stepId: string) {
        selectedStepId = stepId;
    }

    // Resizable panel logic
    let containerEl = $state<HTMLDivElement | null>(null);
    let splitPercent = $state(50); // 50/50 default
    let isDragging = $state(false);

    function onPointerDown(e: PointerEvent) {
        isDragging = true;
        (e.target as HTMLElement).setPointerCapture(e.pointerId);
        e.preventDefault();
    }

    function onPointerMove(e: PointerEvent) {
        if (!isDragging || !containerEl) return;
        const rect = containerEl.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const pct = (x / rect.width) * 100;
        splitPercent = Math.max(25, Math.min(75, pct));
    }

    function onPointerUp() {
        isDragging = false;
    }
</script>

<div
    bind:this={containerEl}
    class="flex h-full select-none"
    class:cursor-col-resize={isDragging}
    onpointermove={onPointerMove}
    onpointerup={onPointerUp}
    role="group"
>
    <!-- Left Panel: Graph -->
    <div
        class="bg-base-100 rounded-lg shadow border border-base-200 overflow-hidden relative"
        style="width: {splitPercent}%; min-width: 200px;"
    >
        <FlowGraph {flow} onNodeClick={handleNodeClick} />

        <div
            class="absolute bottom-4 left-4 bg-base-100/90 backdrop-blur p-2 rounded text-xs text-base-content/50 border border-base-200 pointer-events-none"
        >
            Click nodes to view details
        </div>
    </div>

    <!-- Drag handle -->
    <div
        class="w-2 cursor-col-resize flex items-center justify-center hover:bg-primary/10 transition-colors flex-shrink-0 group z-10 {isDragging
            ? 'bg-primary/20'
            : ''}"
        onpointerdown={onPointerDown}
        role="separator"
        aria-orientation="vertical"
    >
        <div
            class="w-0.5 h-8 bg-base-content/20 rounded-full group-hover:bg-primary/60 transition-colors {isDragging
                ? 'bg-primary'
                : ''}"
        ></div>
    </div>

    <!-- Right Panel: Details -->
    <div
        class="bg-base-100 rounded-lg shadow border border-base-200 overflow-hidden"
        style="width: {100 - splitPercent}%; min-width: 200px;"
    >
        <FlowDetails step={selectedStep} example={selectedExample} />
    </div>
</div>
