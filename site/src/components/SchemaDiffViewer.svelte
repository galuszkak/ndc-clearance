<script lang="ts">
    import { API_URL } from "../utils/config";
    import type { MessageDiff } from "../utils/types";

    let { allVersions }: { allVersions: string[] } = $props();

    // allVersions is static from Astro SSR — initial-value capture is intentional
    const versions = [...allVersions];
    let fromVersion = $state(
        versions.length > 1 ? versions[1] : versions[0],
    );
    let toVersion = $state(versions[0]);

    let diffResults: MessageDiff[] | null = $state(null);
    let loading = $state(false);
    let error: string | null = $state(null);
    let expandedMessages: Record<string, boolean> = $state({});

    async function handleCompare() {
        loading = true;
        error = null;
        diffResults = null;
        expandedMessages = {};

        try {
            const res = await fetch(
                `${API_URL}/api/diff?from=${fromVersion}&to=${toVersion}`,
            );

            if (!res.ok) {
                const text = await res.text();
                throw new Error(text || res.statusText);
            }

            const data = await res.json();

            if (!Array.isArray(data)) {
                throw new Error("Invalid API response: expected an array");
            }

            diffResults = data as MessageDiff[];
        } catch (e: unknown) {
            error = e instanceof Error ? e.message : String(e);
        } finally {
            loading = false;
        }
    }

    /** Escape a field value for CSV output per RFC 4180. */
    function escapeCsvField(field: string | null | undefined): string {
        if (field === null || field === undefined) return "";
        const s = String(field);
        if (s.includes(",") || s.includes("\n") || s.includes('"')) {
            return `"${s.replace(/"/g, '""')}"`;
        }
        return s;
    }

    function exportCSV() {
        if (!diffResults) return;

        const headers = [
            "Message",
            "Message Status",
            "Path",
            "Change Type",
            "Description",
            "Old Value",
            "New Value",
        ];

        let csvContent = headers.join(",") + "\n";

        diffResults.forEach((msg) => {
            if (msg.differences && msg.differences.length > 0) {
                msg.differences.forEach((diff) => {
                    const row = [
                        msg.messageName,
                        msg.status,
                        diff.path,
                        diff.type,
                        diff.description,
                        diff.oldValue || "",
                        diff.newValue || "",
                    ].map(escapeCsvField);
                    csvContent += row.join(",") + "\n";
                });
            } else {
                const row = [
                    msg.messageName,
                    msg.status,
                    "",
                    "",
                    "No structural changes listed",
                    "",
                    "",
                ].map(escapeCsvField);
                csvContent += row.join(",") + "\n";
            }
        });

        const blob = new Blob([csvContent], {
            type: "text/csv;charset=utf-8;",
        });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.setAttribute("href", url);
        link.setAttribute(
            "download",
            `ndc_diff_${fromVersion}_${toVersion}.csv`,
        );
        link.style.visibility = "hidden";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }
</script>

<div class="flex flex-col gap-6">
    <!-- Controls -->
    <div class="card bg-base-100 shadow-xl border border-base-200">
        <div class="card-body">
            <h2 class="card-title mb-4">Compare Schema Versions</h2>
            <div class="flex flex-col md:flex-row gap-4 items-end">
                <div class="form-control w-full max-w-xs">
                    <label class="label" for="fromVersion">
                        <span class="label-text">From Version</span>
                    </label>
                    <select
                        id="fromVersion"
                        class="select select-bordered"
                        bind:value={fromVersion}
                    >
                        {#each allVersions as v (v)}
                            <option value={v}>{v}</option>
                        {/each}
                    </select>
                </div>

                <div class="form-control w-full max-w-xs">
                    <label class="label" for="toVersion">
                        <span class="label-text">To Version</span>
                    </label>
                    <select
                        id="toVersion"
                        class="select select-bordered"
                        bind:value={toVersion}
                    >
                        {#each allVersions as v (v)}
                            <option value={v}>{v}</option>
                        {/each}
                    </select>
                </div>

                <button
                    class="btn btn-primary"
                    onclick={handleCompare}
                    disabled={loading || !fromVersion || !toVersion}
                >
                    {#if loading}
                        <span class="loading loading-spinner"></span>
                    {/if}
                    Compare
                </button>

                {#if diffResults && diffResults.length > 0}
                    <button class="btn btn-outline" onclick={exportCSV}>
                        Export CSV
                    </button>
                {/if}
            </div>
            {#if error}
                <div class="alert alert-error mt-4">
                    <span>{error}</span>
                </div>
            {/if}
        </div>
    </div>

    <!-- Results -->
    {#if diffResults}
        <div class="space-y-4">
            <h3 class="text-xl font-bold">Results</h3>

            {#if diffResults.length === 0}
                <p>
                    No differences found between {fromVersion} and {toVersion}.
                </p>
            {/if}

            {#each diffResults as msg (msg.messageName)}
                <div
                    class="bg-base-100 border border-base-200 rounded-box overflow-hidden"
                >
                    <button
                        class="w-full text-left px-6 py-4 text-xl font-medium flex flex-wrap items-center gap-3 cursor-pointer hover:bg-base-200/50 transition-colors"
                        aria-expanded={!!expandedMessages[msg.messageName]}
                        aria-label={`Toggle ${msg.messageName} details`}
                        onclick={() => {
                            expandedMessages[msg.messageName] =
                                !expandedMessages[msg.messageName];
                        }}
                    >
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            class="h-4 w-4 transition-transform flex-none {expandedMessages[msg.messageName]
                                ? 'rotate-90'
                                : ''}"
                            viewBox="0 0 20 20"
                            fill="currentColor"
                        >
                            <path
                                fill-rule="evenodd"
                                d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                                clip-rule="evenodd"
                            />
                        </svg>
                        <span>{msg.messageName}</span>
                        {#if msg.status === "ADDED"}
                            <div class="badge badge-success gap-2">ADDED</div>
                        {:else if msg.status === "REMOVED"}
                            <div class="badge badge-error gap-2">REMOVED</div>
                        {:else if msg.status === "CHANGED"}
                            <div class="badge badge-warning gap-2">CHANGED</div>
                            <span class="text-sm font-normal opacity-50"
                                >({msg.differences.length} changes)</span
                            >
                        {/if}
                    </button>
                    {#if expandedMessages[msg.messageName]}
                        <div class="px-6 pb-4">
                            <!-- View Version Buttons -->
                            <div class="flex gap-2 mb-4 mt-2">
                                {#if msg.status === "ADDED"}
                                    <a
                                        href={`/${toVersion}/${msg.messageName}`}
                                        class="btn btn-sm btn-outline btn-success"
                                        target="_blank">View {toVersion}</a
                                    >
                                {:else if msg.status === "REMOVED"}
                                    <a
                                        href={`/${fromVersion}/${msg.messageName}`}
                                        class="btn btn-sm btn-outline btn-error"
                                        target="_blank">View {fromVersion}</a
                                    >
                                {:else if msg.status === "CHANGED"}
                                    <a
                                        href={`/${fromVersion}/${msg.messageName}`}
                                        class="btn btn-sm btn-outline btn-error"
                                        target="_blank">View {fromVersion}</a
                                    >
                                    <span class="self-center opacity-30">→</span
                                    >
                                    <a
                                        href={`/${toVersion}/${msg.messageName}`}
                                        class="btn btn-sm btn-outline btn-success"
                                        target="_blank">View {toVersion}</a
                                    >
                                {/if}
                            </div>

                            {#if msg.differences && msg.differences.length > 0}
                                <div class="overflow-x-auto">
                                    <table
                                        class="table table-fixed table-zebra w-full"
                                    >
                                        <thead>
                                            <tr>
                                                <th class="w-1/2">Path</th>
                                                <th class="w-32">Type</th>
                                                <th class="w-1/2"
                                                    >Description</th
                                                >
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {#each msg.differences as diff (diff.path + diff.type)}
                                                <tr>
                                                    <td
                                                        class="font-mono text-xs break-all align-top"
                                                        >{diff.path}</td
                                                    >
                                                    <td class="align-top">
                                                        <span
                                                            class={`badge badge-sm ${
                                                                diff.type ===
                                                                "ADDED"
                                                                    ? "badge-success"
                                                                    : diff.type ===
                                                                        "REMOVED"
                                                                      ? "badge-error"
                                                                      : diff.type ===
                                                                          "MODIFIED"
                                                                        ? "badge-warning"
                                                                        : "badge-info"
                                                            }`}
                                                        >
                                                            {diff.type}
                                                        </span>
                                                    </td>
                                                    <td
                                                        class="text-sm align-top"
                                                    >
                                                        <div>
                                                            {diff.description}
                                                        </div>
                                                        {#if diff.oldValue || diff.newValue}
                                                            <div
                                                                class="grid grid-cols-2 gap-2 mt-1 text-xs bg-base-200 p-2 rounded"
                                                            >
                                                                {#if diff.oldValue}
                                                                    <div
                                                                        class="text-error break-all"
                                                                    >
                                                                        - {diff.oldValue}
                                                                    </div>
                                                                {/if}
                                                                {#if diff.newValue}
                                                                    <div
                                                                        class="text-success break-all"
                                                                    >
                                                                        + {diff.newValue}
                                                                    </div>
                                                                {/if}
                                                            </div>
                                                        {/if}
                                                    </td>
                                                </tr>
                                            {/each}
                                        </tbody>
                                    </table>
                                </div>
                            {:else}
                                <p class="py-4 opacity-70">
                                    No structural changes detected.
                                </p>
                            {/if}
                        </div>
                    {/if}
                </div>
            {/each}
        </div>
    {/if}
</div>
