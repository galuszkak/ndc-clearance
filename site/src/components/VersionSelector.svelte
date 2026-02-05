<script lang="ts">
    export let currentVersion: string;
    export let currentMessage: string;
    export let allVersions: string[];

    async function handleVersionChange(event: Event) {
        const newVersion = (event.target as HTMLSelectElement).value;
        if (newVersion === currentVersion) return;

        // Check if message exists in new version
        // The structure is /schemas/[version]/[message]/IATA_[message].xsd
        const targetUrl = `/schemas/${newVersion}/${currentMessage}/IATA_${currentMessage}.xsd`;

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
        } catch (e) {
            console.error(e);
            window.location.href = `/`;
        }
    }
</script>

<select
    class="select select-bordered select-sm w-full max-w-xs"
    value={currentVersion}
    on:change={handleVersionChange}
>
    {#each allVersions as v}
        <option value={v}>Version {v}</option>
    {/each}
</select>
