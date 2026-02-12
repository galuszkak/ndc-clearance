<script lang="ts">
    import { schemaStats } from "../utils/stores";
</script>

<div class="px-4 py-2">
    <div class="font-bold text-xs uppercase tracking-wider opacity-50 mb-4">
        Structure Overview
    </div>
    <div class="space-y-4">
        <div class="flex justify-between items-center text-sm">
            <span class="opacity-70">Elements</span>
            <span class="badge badge-sm badge-outline font-mono"
                >{$schemaStats.elements}</span
            >
        </div>
        <div class="flex justify-between items-center text-sm">
            <span class="opacity-70">Complex Types</span>
            <span class="badge badge-sm badge-outline font-mono"
                >{$schemaStats.complexTypes}</span
            >
        </div>
        <div class="flex justify-between items-center text-sm">
            <span class="opacity-70">Simple Types</span>
            <span class="badge badge-sm badge-outline font-mono"
                >{$schemaStats.simpleTypes}</span
            >
        </div>
    </div>
</div>
