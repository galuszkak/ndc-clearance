<script lang="ts">
    export let currentVersion: string;
    export let currentMessage: string;
    export let allVersions: string[];

    async function handleVersionChange(event: Event) {
        const newVersion = (event.target as HTMLSelectElement).value;
        if (newVersion === currentVersion) return;

        // Check if message exists in new version
        const targetUrl = `/schemas/${newVersion}/${currentMessage}.xsd`;
        try {
            const res = await fetch(targetUrl, { method: 'HEAD' });
            if (res.ok) {
                window.location.href = `/${newVersion}/${currentMessage}`;
            } else {
                // Fallback to index of that version (which we don't strictly have a page for, but we have /[version]/FirstMessage)
                // Or just alert user? 
                // Better: Redirect to the first message of that version?
                // For now, let's try to guess or just go to root of version if I implemented that.
                // I implemented /[version]/[message]. I don't have /[version].
                // Let's just warn or stay put?
                // The requirements say "it should show him same message type". Implicitly, if not exists, maybe show error?
                // Let's redirect to home of that version, which is effectively the dashboard?
                // No, I'll redirect to the landing page or just alert.
                 alert(`Message ${currentMessage} does not exist in version ${newVersion}. Redirecting to home.`);
                 window.location.href = `/`;
            }
        } catch (e) {
            console.error(e);
            window.location.href = `/`;
        }
    }
</script>

<select class="select select-bordered select-sm w-full max-w-xs" value={currentVersion} on:change={handleVersionChange}>
    {#each allVersions as v}
        <option value={v}>Version {v}</option>
    {/each}
</select>
