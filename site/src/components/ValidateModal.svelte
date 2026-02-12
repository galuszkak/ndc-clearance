<script lang="ts">
    let {
        version,
        message,
        apiBaseUrl,
    }: {
        version: string;
        message: string;
        apiBaseUrl: string;
    } = $props();

    let xmlInput = $state("");
    let validating = $state(false);
    let result: { valid: boolean; errors: string[] } | null = $state(null);
    let errorMessage: string | null = $state(null);
    let dialogRef: HTMLDialogElement | undefined = $state(undefined);

    function open() {
        dialogRef?.showModal();
    }

    async function validate() {
        if (!xmlInput.trim()) return;

        validating = true;
        result = null;
        errorMessage = null;

        try {
            const response = await fetch(`${apiBaseUrl}/validate`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ version, message, xml: xmlInput }),
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(
                    errorText ||
                        `Validation failed: ${response.status} ${response.statusText}`,
                );
            }

            const data = await response.json();

            if (typeof data.valid !== "boolean") {
                throw new Error("Invalid API response: missing 'valid' field");
            }

            let errors: string[] = [];
            if (Array.isArray(data.errors)) {
                errors = data.errors.map(String);
            } else if (typeof data.errors === "string") {
                errors = [data.errors];
            }

            result = { valid: data.valid, errors };
        } catch (e: unknown) {
            errorMessage = e instanceof Error ? e.message : String(e);
        } finally {
            validating = false;
        }
    }
</script>

<button class="btn btn-secondary btn-sm w-full sm:w-auto" onclick={open}>
    Validate Message
</button>

<dialog bind:this={dialogRef} class="modal" aria-label="Validate XML Message">
    <div class="modal-box w-11/12 max-w-5xl h-[80vh] flex flex-col">
        <h3 class="font-bold text-lg mb-4">Validate XML Message</h3>
        <div class="flex-1 flex flex-col gap-4 overflow-hidden">
            <div class="flex-1 relative">
                <textarea
                    class="textarea textarea-bordered w-full h-full font-mono text-xs"
                    placeholder="Paste your XML message here..."
                    bind:value={xmlInput}
                ></textarea>
            </div>

            <div aria-live="polite" role="status">
            {#if validating}
                <div
                    class="p-4 rounded-lg overflow-y-auto max-h-48 border bg-base-200 animate-pulse"
                >
                    Validating...
                </div>
            {:else if result}
                {#if result.valid}
                    <div
                        class="p-4 rounded-lg overflow-y-auto max-h-48 border bg-success/10 border-success text-success-content"
                    >
                        <div class="flex items-center gap-2 font-bold">
                            <svg
                                xmlns="http://www.w3.org/2000/svg"
                                class="h-6 w-6"
                                fill="none"
                                viewBox="0 0 24 24"
                                stroke="currentColor"
                            >
                                <path
                                    stroke-linecap="round"
                                    stroke-linejoin="round"
                                    stroke-width="2"
                                    d="M5 13l4 4L19 7"
                                />
                            </svg>
                            Valid Message
                        </div>
                    </div>
                {:else}
                    <div
                        class="p-4 rounded-lg overflow-y-auto max-h-48 border bg-error/10 border-error text-error-content font-mono text-xs"
                    >
                        <div class="font-bold mb-2">Validation Errors:</div>
                        <ul class="list-disc pl-4">
                            {#each result.errors as err, i (i)}
                                <li>{err}</li>
                            {/each}
                        </ul>
                    </div>
                {/if}
            {:else if errorMessage}
                <div
                    class="p-4 rounded-lg overflow-y-auto max-h-48 border bg-error/10 border-error text-error-content"
                >
                    Error: {errorMessage}. Ensure the backend API is reachable
                    at {apiBaseUrl}.
                </div>
            {/if}
            </div>
        </div>
        <div class="modal-action flex justify-between items-center">
            <div class="text-xs opacity-50">
                Validating against schema: {version}/{message}
            </div>
            <div class="flex gap-2">
                <button class="btn btn-primary" onclick={validate}>
                    Validate
                </button>
                <form method="dialog">
                    <button class="btn">Close</button>
                </form>
            </div>
        </div>
    </div>
    <form method="dialog" class="modal-backdrop">
        <button>close</button>
    </form>
</dialog>
