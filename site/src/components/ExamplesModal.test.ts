import { describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/svelte";
import userEvent from "@testing-library/user-event";
import ExamplesModal from "./ExamplesModal.svelte";
import type { ExampleRecord } from "../utils/types";

vi.mock("../utils/highlight", () => ({
    hljs: {
        highlightElement: vi.fn(),
    },
}));

describe("ExamplesModal", () => {
    it("loads preview XML via public_path", async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            text: () => Promise.resolve("<IATA_OrderCreateRQ/>"),
        });
        vi.stubGlobal("fetch", fetchMock);

        const examples: ExampleRecord[] = [
            {
                id: "ex_123456789abc",
                source: "iata",
                message: "IATA_OrderCreateRQ",
                version: "24.1",
                title: "Sample",
                description: null,
                tags: [],
                file_name: "sample.xml",
                xml_path: "ndc_content/examples/files/iata/ex_123456789abc.xml",
                public_path: "/content/examples/files/iata/ex_123456789abc.xml",
                source_url: "https://example.test",
                source_page_id: "123",
                flow_id: null,
                is_active: true,
            },
        ];

        const user = userEvent.setup();
        const { container } = render(ExamplesModal, {
            props: {
                examples,
                message: "IATA_OrderCreateRQ",
            },
        });

        await user.click(container.querySelector("button.btn-primary") as HTMLButtonElement);

        await waitFor(() => {
            expect(fetchMock).toHaveBeenCalledTimes(1);
        });

        const requestedUrl = String(fetchMock.mock.calls[0][0]);
        expect(requestedUrl.startsWith("/content/examples/files/iata/ex_123456789abc.xml?t=")).toBe(true);
        expect(container.textContent).toContain("<IATA_OrderCreateRQ/>");

        vi.restoreAllMocks();
    });
});
