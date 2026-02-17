<script lang="ts">
    import { MODAL_IDS } from "../utils/constants";
    import type { FlowRecord } from "../utils/types";

    let {
        flows = [],
        message = "",
    }: {
        flows: FlowRecord[];
        message: string;
    } = $props();
</script>

<dialog id={MODAL_IDS.FLOWS} class="modal">
    <div class="modal-box w-11/12 max-w-3xl flex flex-col p-6">
        <div class="flex justify-between items-center mb-6">
            <h3 class="font-bold text-xl flex items-center gap-2">
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
                        d="M13 10V3L4 14h7v7l9-11h-7z"
                    />
                </svg>
                Flows using {message.replace(/^IATA_/, "")}
            </h3>
            <form method="dialog">
                <button class="btn btn-sm btn-circle btn-ghost">✕</button>
            </form>
        </div>

        <div class="flex-1 overflow-auto min-h-0">
            <div class="grid gap-4">
                {#each flows as flow (flow.id)}
                    {@const relevantSteps = flow.steps.filter(
                        (s) => s.message === message,
                    )}
                    <a
                        href={`/flows/${flow.id}`}
                        class="group card bg-base-200 hover:bg-base-300 transition-all duration-300 border border-base-content/5 shadow-sm hover:shadow-md no-underline"
                    >
                        <div class="card-body p-5">
                            <div class="flex justify-between items-start gap-4">
                                <div class="flex-1">
                                    <h4 class="font-bold text-lg leading-snug">
                                        {flow.title}
                                    </h4>
                                    <p
                                        class="text-sm text-base-content/60 mt-1 line-clamp-2"
                                    >
                                        {flow.description}
                                    </p>
                                    <div
                                        class="flex flex-wrap gap-1.5 mt-3 items-center"
                                    >
                                        <span
                                            class="badge badge-sm"
                                            class:badge-primary={flow.status ===
                                                "active" ||
                                                (flow.status as string) ===
                                                    "iata"}
                                            class:badge-ghost={flow.status ===
                                                "draft"}
                                            class:badge-warning={flow.status ===
                                                "deprecated"}
                                        >
                                            {flow.status}
                                        </span>
                                        <span
                                            class="badge badge-sm badge-outline opacity-60"
                                        >
                                            {flow.steps.length} steps
                                        </span>
                                        {#each relevantSteps as step}
                                            <span
                                                class="badge badge-sm badge-secondary badge-outline"
                                            >
                                                Step {step.order}
                                            </span>
                                        {/each}
                                    </div>
                                </div>
                                <div class="flex-none">
                                    <span
                                        class="btn btn-primary btn-sm px-6 shadow-sm"
                                    >
                                        View Flow
                                    </span>
                                </div>
                            </div>
                        </div>
                    </a>
                {/each}
            </div>
        </div>

        <div class="modal-action">
            <form method="dialog">
                <button class="btn">Close</button>
            </form>
        </div>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button>close</button>
    </form>
</dialog>
