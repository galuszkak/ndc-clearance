<script lang="ts">
    export let allVersions: string[];

    let fromVersion = allVersions.length > 1 ? allVersions[1] : allVersions[0];
    let toVersion = allVersions[0];

    interface DiffItem {
        path: string;
        type: "ADDED" | "REMOVED" | "MODIFIED" | "DOC_CHANGED";
        description: string;
        oldValue?: string;
        newValue?: string;
    }

    interface MessageDiff {
        messageName: string;
        differences: DiffItem[];
        status: "ADDED" | "REMOVED" | "CHANGED" | "UNCHANGED";
    }

    let diffResults: MessageDiff[] | null = null;
    let loading = false;
    let error: string | null = null;

    async function handleCompare() {
        loading = true;
        error = null;
        diffResults = null;

        try {
            // Assuming backend is running on localhost:8080 for dev.
            // In a real setup, this might need an environment variable.
            const apiUrl =
                import.meta.env.PUBLIC_API_URL || "http://localhost:8080";
            const res = await fetch(
                `${apiUrl}/api/diff?from=${fromVersion}&to=${toVersion}`,
            );

            if (!res.ok) {
                const text = await res.text();
                throw new Error(text || res.statusText);
            }

            diffResults = await res.json();
        } catch (e: any) {
            error = e.message;
        } finally {
            loading = false;
        }
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
                        {#each allVersions as v}
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
                        {#each allVersions as v}
                            <option value={v}>{v}</option>
                        {/each}
                    </select>
                </div>

                <button
                    class="btn btn-primary"
                    on:click={handleCompare}
                    disabled={loading || !fromVersion || !toVersion}
                >
                    {#if loading}
                        <span class="loading loading-spinner"></span>
                    {/if}
                    Compare
                </button>
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

            {#each diffResults as msg}
                <div
                    class="collapse collapse-arrow bg-base-100 border border-base-200 rounded-box"
                >
                    <input type="checkbox" />
                    <div
                        class="collapse-title text-xl font-medium flex items-center gap-3"
                    >
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
                    </div>
                    <div class="collapse-content">
                        {#if msg.differences && msg.differences.length > 0}
                            <div class="overflow-x-auto">
                                <table
                                    class="table table-fixed table-zebra w-full"
                                >
                                    <thead>
                                        <tr>
                                            <th class="w-1/2">Path</th>
                                            <th class="w-32">Type</th>
                                            <th class="w-1/2">Description</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {#each msg.differences as diff}
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
                                                <td class="text-sm align-top">
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
                </div>
            {/each}
        </div>
    {/if}
</div>
