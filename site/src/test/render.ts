import { render as svelteRender } from "@testing-library/svelte";

export { screen, waitFor } from "@testing-library/svelte";

export const render = svelteRender as unknown as (
    component: unknown,
    options?: Record<string, unknown>,
    renderOptions?: Record<string, unknown>,
) => ReturnType<typeof svelteRender>;
