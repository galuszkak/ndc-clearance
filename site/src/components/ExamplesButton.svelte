<script lang="ts">
    import ExamplesModal from "./ExamplesModal.svelte";
    import { MODAL_IDS } from "../utils/constants";
    import { openModal } from "../utils/modal";

    let { examples = [], message = "" } = $props();
</script>

<button class="btn btn-secondary btn-sm w-full sm:w-auto" onclick={() => openModal(MODAL_IDS.EXAMPLES)} aria-label={`View examples (${examples.length})`}>
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
            d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
        />
    </svg>
    Examples
    <div class="badge badge-sm badge-outline opacity-50 ml-1">
        {examples.length}
    </div>
</button>

<ExamplesModal {examples} {message} />
