<script lang="ts">
    import { IATA_PREFIX } from "../utils/constants";

    let {
        currentVersion,
        currentMessage,
        allVersions,
    }: {
        currentVersion: string;
        currentMessage: string;
        allVersions: string[];
    } = $props();

    async function handleVersionChange(event: Event) {
        const newVersion = (event.target as HTMLSelectElement).value;
        if (newVersion === currentVersion) return;

        const targetUrl = `/schemas/${newVersion}/${currentMessage}/${IATA_PREFIX}${currentMessage}.xsd`;

        try {
            const res = await fetch(targetUrl, { method: "HEAD" });
            if (res.ok) {
                window.location.href = `/${newVersion}/${currentMessage}`;
            } else {
                alert(
                    `Message ${currentMessage} does not exist in version ${newVersion}. Redirecting to home.`,
                );
                window.location.href = `/`;
            }
        } catch {
            window.location.href = `/`;
        }
    }
</script>

<select
    class="select select-bordered select-sm w-full max-w-xs"
    value={currentVersion}
    onchange={handleVersionChange}
>
    {#each allVersions as v (v)}
        <option value={v}>Version {v}</option>
    {/each}
</select>
