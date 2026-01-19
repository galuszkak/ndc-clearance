import { writable } from 'svelte/store';

export const schemaStats = writable({
    elements: 0,
    complexTypes: 0,
    simpleTypes: 0
});
