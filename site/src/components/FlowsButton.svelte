<script lang="ts">
    import FlowsModal from "./FlowsModal.svelte";
    import { MODAL_IDS } from "../utils/constants";
    import { openModal } from "../utils/modal";
    import type { FlowRecord } from "../utils/types";

    let { flows = [], message = "" }: { flows: FlowRecord[]; message: string } =
        $props();
</script>

<button
    class="btn btn-secondary btn-sm w-full sm:w-auto"
    onclick={() => openModal(MODAL_IDS.FLOWS)}
    aria-label={`View flows (${flows.length})`}
>
    <svg
        xmlns="http://www.w3.org/2000/svg"
        class="h-4 w-4 mr-1"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
    >
        <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M13 10V3L4 14h7v7l9-11h-7z"
        />
    </svg>
    Flows
    <div class="badge badge-sm badge-outline opacity-50 ml-1">
        {flows.length}
    </div>
</button>

<FlowsModal {flows} {message} />
