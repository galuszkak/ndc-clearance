<script lang="ts">
    import { onMount } from "svelte";
    import SchemaNode from "./SchemaNode.svelte";
    import { schemaStats } from "../utils/stores";

    export let schemaUrl: string;

    let doc: XMLDocument | null = null;
    let rootElement: Element | null = null;
    let definitions: Record<string, Element> = {};
    let loading = true;
    let error = "";

    // Search & Interaction State
    let query = "";
    let selectedNode: Element | null = null;
    let selectedPath = "";
    let selectedHelper: {
        typeName?: string;
        min?: string;
        max?: string;
        doc?: string;
        regex?: string;
    } = {};

    let treeAction: "expand" | "collapse" | "" = "";
    let treeActionVersion = 0;
    let zoom = 100;

    let searchInput: HTMLInputElement;

    function handleKeyDown(e: KeyboardEvent) {
        if ((e.ctrlKey || e.metaKey) && e.key === "f") {
            // Only take over if not already in another input (unless it's our own)
            if (
                document.activeElement?.tagName === "INPUT" ||
                document.activeElement?.tagName === "TEXTAREA"
            ) {
                if (document.activeElement !== searchInput) return;
            }

            e.preventDefault();
            if (searchInput) {
                searchInput.focus();
                searchInput.select();
            }
        }
    }

    async function loadSchema() {
        loading = true;
        error = "";
        selectedNode = null;
        selectedPath = "";
        try {
            const res = await fetch(schemaUrl);
            if (!res.ok)
                throw new Error(`Failed to load schema: ${res.statusText}`);
            const text = await res.text();
            const parser = new DOMParser();
            doc = parser.parseFromString(text, "application/xml");

            const parseError = doc.querySelector("parsererror");
            if (parseError) throw new Error("XML Parse Error");

            // Build Definition Map
            definitions = {};
            const schema = doc.documentElement;

            for (const child of Array.from(schema.children)) {
                const name = child.getAttribute("name");
                if (name) {
                    const key = `${child.localName}:${name}`;
                    definitions[key] = child;
                }
            }

            // Determine the expected root element name from the URL
            const filename = schemaUrl.split("/").pop() || "";
            const expectedRootName = filename.replace(".xsd", "");

            // Find the root element: try to match filename, otherwise pick the first element found
            rootElement =
                Array.from(schema.children).find(
                    (n) =>
                        n.localName === "element" &&
                        n.getAttribute("name") === expectedRootName,
                ) ||
                Array.from(schema.children).find(
                    (n) => n.localName === "element",
                ) ||
                null;
            if (rootElement) {
                const path = `/${rootElement.getAttribute("name")}`;
                handleSelect({ node: rootElement, path });
            }

            // Update stats
            schemaStats.set({
                elements: Array.from(schema.children).filter(
                    (n) => n.localName === "element",
                ).length,
                complexTypes: Array.from(schema.children).filter(
                    (n) => n.localName === "complexType",
                ).length,
                simpleTypes: Array.from(schema.children).filter(
                    (n) => n.localName === "simpleType",
                ).length,
            });
        } catch (e: any) {
            error = e.message;
        } finally {
            loading = false;
        }
    }

    function handleSelect(detail: { node: Element; path: string }) {
        selectedNode = detail.node;
        selectedPath = detail.path;
        const node = detail.node;

        // Extract helpers for the right panel
        let typeName = node.getAttribute("type");
        if (typeName && typeName.includes(":"))
            typeName = typeName.split(":")[1];

        // If it's a ref, resolve for display
        if (node.getAttribute("ref")) {
            const refName = node.getAttribute("ref")?.split(":").pop();
            if (refName) {
                const def = definitions[`element:${refName}`];
                if (def) {
                    typeName = def.getAttribute("type") || typeName;
                    if (typeName && typeName.includes(":"))
                        typeName = typeName.split(":")[1];
                }
            }
        }

        const getDocumentation = (el: Element) => {
            const annotation = Array.from(el.children).find(
                (c) => c.localName === "annotation",
            );
            if (annotation) {
                const docNode = Array.from(annotation.children).find(
                    (c) => c.localName === "documentation",
                );
                return docNode ? docNode.textContent || "" : "";
            }
            return "";
        };

        let docText = getDocumentation(node);

        selectedHelper = {
            typeName:
                typeName ||
                (Array.from(node.children).find(
                    (c) => c.localName === "complexType",
                )
                    ? "Anonymous ComplexType"
                    : "Anonymous SimpleType"),
            min: node.getAttribute("minOccurs") || "1",
            max: node.getAttribute("maxOccurs") || "1",
            doc: docText || "No description available.",
            regex: "",
        };
    }

    // Event receiver from SchemaNode
    function onNodeSelect(detail: { node: Element; path: string }) {
        handleSelect(detail);
    }

    // Search Logic
    let searchResults: {
        name: string;
        path: string;
        type: string;
        doc: string;
    }[] = [];
    let isSearching = false;
    let targetPath = "";

    function resolveChildren(
        node: Element,
        definitions: Record<string, Element>,
    ): Element[] {
        let children: Element[] = [];
        let typeName = node.getAttribute("type");
        if (typeName && typeName.includes(":"))
            typeName = typeName.split(":")[1];

        // Resolve Ref
        if (node.getAttribute("ref")) {
            const refName = node.getAttribute("ref")?.split(":").pop();
            if (refName) {
                const def = definitions[`element:${refName}`];
                if (def) {
                    typeName =
                        def.getAttribute("type")?.split(":").pop() || typeName;
                    // If ref has no type, it might be inline complextype in definition
                    if (!typeName) {
                        const inline = Array.from(def.children).find(
                            (c) => c.localName === "complexType",
                        );
                        if (inline) {
                            return collectElements(inline, definitions);
                        }
                    }
                }
            }
        }

        let typeDefinition = typeName
            ? definitions[`complexType:${typeName}`] ||
              definitions[`simpleType:${typeName}`]
            : null;
        if (!typeDefinition) {
            typeDefinition =
                Array.from(node.children).find(
                    (c) =>
                        c.localName === "complexType" ||
                        c.localName === "simpleType",
                ) || null;
        }

        if (typeDefinition) {
            children = collectElements(typeDefinition, definitions);
        }
        return children;
    }

    function collectElements(
        root: Element,
        definitions: Record<string, Element>,
    ): Element[] {
        const els: Element[] = [];
        for (const child of Array.from(root.children)) {
            if (child.localName === "element") {
                els.push(child);
            } else if (
                ["sequence", "choice", "all"].includes(child.localName)
            ) {
                els.push(...collectElements(child, definitions));
            } else if (child.localName === "complexContent") {
                for (const cc of Array.from(child.children)) {
                    if (
                        cc.localName === "extension" ||
                        cc.localName === "restriction"
                    ) {
                        const base = cc.getAttribute("base")?.split(":").pop();
                        if (base) {
                            const baseDef = definitions[`complexType:${base}`];
                            if (baseDef) {
                                els.push(
                                    ...collectElements(baseDef, definitions),
                                );
                            }
                        }
                        els.push(...collectElements(cc, definitions));
                    }
                }
            } else if (
                child.localName === "complexType" ||
                child.localName === "simpleType"
            ) {
                els.push(...collectElements(child, definitions));
            }
        }
        return els;
    }

    async function performSearch() {
        if (!query || query.length < 2) {
            searchResults = [];
            return;
        }
        isSearching = true;
        searchResults = [];

        // BFS Search
        const q = [
            {
                node: rootElement!,
                path: `/${rootElement!.getAttribute("name")}`,
            },
        ];
        let count = 0;

        while (q.length > 0 && count < 1000) {
            // Limit search space
            const { node, path } = q.shift()!;
            count++;

            const name =
                node.getAttribute("name") ||
                node.getAttribute("ref")?.split(":").pop() ||
                "";

            // Check match
            if (name.toLowerCase().includes(query.toLowerCase())) {
                searchResults.push({
                    name,
                    path,
                    type: "Element",
                    doc: "",
                });
            }
            if (searchResults.length > 20) break; // Limit results

            // Get children
            const children = resolveChildren(node, definitions);
            for (const child of children) {
                const childName =
                    child.getAttribute("name") ||
                    child.getAttribute("ref")?.split(":").pop();
                if (childName) {
                    // Check for recursion
                    if (!path.includes(`/${childName}/`)) {
                        q.push({ node: child, path: `${path}/${childName}` });
                    }
                }
            }
        }
        isSearching = false;
        // Trigger generic svelte update
        searchResults = searchResults;
    }

    let searchDebounce: any;
    $: if (query) {
        clearTimeout(searchDebounce);
        searchDebounce = setTimeout(performSearch, 300);
    } else {
        searchResults = [];
    }

    function selectResult(result: { path: string; node?: Element }) {
        targetPath = result.path;
        selectedPath = result.path;
        searchResults = []; // Close results
        query = ""; // Clear query
    }

    $: if (schemaUrl) loadSchema();
</script>

<svelte:window on:keydown={handleKeyDown} />

<div class="flex flex-col h-full gap-4">
    <!-- XPath Bar -->
    {#if selectedPath}
        <div
            class="bg-base-100 rounded-lg shadow-sm border border-base-200 p-2 px-4 flex items-center text-xs font-mono text-primary/80 overflow-hidden"
        >
            <span class="opacity-50 mr-2 select-none flex-none">XPATH:</span>
            <span class="select-all truncate min-w-0" title={selectedPath}
                >{selectedPath}</span
            >
        </div>
    {/if}

    <div class="flex flex-col lg:flex-row flex-1 gap-4 overflow-hidden">
        <!-- Left Panel: Tree & Search -->
        <div
            class="flex-1 flex flex-col bg-base-100 rounded-lg shadow border border-base-200 overflow-hidden"
        >
            <!-- Search Bar -->
            <div class="p-3 border-b border-base-200 bg-base-50 relative z-20">
                <div class="relative">
                    <input
                        bind:this={searchInput}
                        type="text"
                        placeholder="Search elements..."
                        class="input input-sm input-bordered w-full pr-16"
                        bind:value={query}
                    />
                    <div
                        class="absolute inset-y-0 right-10 flex items-center pointer-events-none"
                    >
                        <kbd
                            class="kbd kbd-xs bg-base-200 border-base-300 opacity-60 font-sans px-1"
                        >
                            {typeof window !== "undefined" &&
                            /Mac|iPod|iPhone|iPad/.test(navigator.platform)
                                ? "⌘F"
                                : "Ctrl+F"}
                        </kbd>
                    </div>
                    <div
                        class="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none text-base-content/40"
                    >
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            class="h-4 w-4"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                            ><path
                                stroke-linecap="round"
                                stroke-linejoin="round"
                                stroke-width="2"
                                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                            /></svg
                        >
                    </div>
                </div>

                {#if searchResults.length > 0}
                    <div
                        class="absolute top-full left-0 right-0 bg-base-100 shadow-xl border border-base-200 rounded-b-lg max-h-64 overflow-y-auto"
                    >
                        <ul class="menu menu-compact p-0">
                            {#each searchResults as result}
                                <li>
                                    <button
                                        class="flex flex-col items-start gap-0 py-2 border-b border-base-100 last:border-0"
                                        on:click={() => selectResult(result)}
                                    >
                                        <span class="font-bold text-xs"
                                            >{result.name}</span
                                        >
                                        <span
                                            class="text-[10px] opacity-50 font-mono truncate w-full text-left"
                                            >{result.path}</span
                                        >
                                    </button>
                                </li>
                            {/each}
                        </ul>
                    </div>
                {/if}
            </div>

            <!-- Tree View -->
            <div class="flex-1 overflow-auto p-4 relative bg-base-50/30">
                {#if loading}
                    <div class="flex justify-center p-10">
                        <span class="loading loading-spinner text-primary"
                        ></span>
                    </div>
                {:else if error}
                    <div class="alert alert-error text-sm">
                        <span>{error}</span>
                    </div>
                {:else if rootElement && doc}
                    <div
                        class="font-mono text-sm leading-relaxed origin-top-left transition-transform"
                        style="transform: scale({zoom / 100})"
                    >
                        <SchemaNode
                            node={rootElement}
                            {doc}
                            {definitions}
                            path={`/${rootElement.getAttribute("name")}`}
                            searchQuery={query.toLowerCase()}
                            {targetPath}
                            {selectedPath}
                            {treeAction}
                            {treeActionVersion}
                            onselect={onNodeSelect}
                        />
                    </div>
                {:else}
                    <div class="alert alert-warning text-sm">
                        No Root Element found
                    </div>
                {/if}

                <!-- Floating Toolbar -->
                <div class="absolute bottom-6 left-1/2 -translate-x-1/2 z-20">
                    <div
                        class="flex items-center gap-2 p-1.5 bg-base-100/90 backdrop-blur shadow-xl border border-base-200 rounded-full transition-all hover:scale-105 active:scale-95"
                    >
                        <!-- Zoom Controls -->
                        <div
                            class="join join-horizontal shadow-sm border border-base-200 rounded-full"
                        >
                            <button
                                class="btn btn-xs btn-ghost join-item px-2"
                                on:click={() =>
                                    (zoom = Math.max(50, zoom - 10))}
                                title="Zoom Out"
                            >
                                <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    class="h-3 w-3"
                                    fill="none"
                                    viewBox="0 0 24 24"
                                    stroke="currentColor"
                                    ><path
                                        stroke-linecap="round"
                                        stroke-linejoin="round"
                                        stroke-width="2"
                                        d="M20 12H4"
                                    /></svg
                                >
                            </button>
                            <div
                                class="join-item px-2 flex items-center bg-base-100 text-[10px] font-mono font-bold w-10 justify-center select-none"
                            >
                                {zoom}%
                            </div>
                            <button
                                class="btn btn-xs btn-ghost join-item px-2"
                                on:click={() =>
                                    (zoom = Math.min(200, zoom + 10))}
                                title="Zoom In"
                            >
                                <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    class="h-3 w-3"
                                    fill="none"
                                    viewBox="0 0 24 24"
                                    stroke="currentColor"
                                    ><path
                                        stroke-linecap="round"
                                        stroke-linejoin="round"
                                        stroke-width="2"
                                        d="M12 4v16m8-8H4"
                                    /></svg
                                >
                            </button>
                        </div>

                        <div class="w-px h-4 bg-base-300 mx-1"></div>

                        <!-- Tree Controls -->
                        <div
                            class="join join-horizontal shadow-sm border border-base-200 rounded-full bg-base-100"
                        >
                            <button
                                class="btn btn-xs btn-ghost join-item rounded-l-full normal-case font-normal px-3"
                                on:click={() => {
                                    treeAction = "expand";
                                    treeActionVersion++;
                                    query = "";
                                }}
                            >
                                <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    class="h-3 w-3 mr-1 opacity-60"
                                    fill="none"
                                    viewBox="0 0 24 24"
                                    stroke="currentColor"
                                    ><path
                                        stroke-linecap="round"
                                        stroke-linejoin="round"
                                        stroke-width="2"
                                        d="M19 13l-7 7-7-7m14-8l-7 7-7-7"
                                    /></svg
                                >
                                Expand All
                            </button>
                            <div class="w-px bg-base-200 h-full"></div>
                            <button
                                class="btn btn-xs btn-ghost join-item rounded-r-full normal-case font-normal px-3"
                                on:click={() => {
                                    treeAction = "collapse";
                                    treeActionVersion++;
                                    query = "";
                                }}
                            >
                                <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    class="h-3 w-3 mr-1 opacity-60"
                                    fill="none"
                                    viewBox="0 0 24 24"
                                    stroke="currentColor"
                                    ><path
                                        stroke-linecap="round"
                                        stroke-linejoin="round"
                                        stroke-width="2"
                                        d="M5 11l7-7 7 7M5 19l7-7 7 7"
                                    /></svg
                                >
                                Collapse All
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Status Bar -->
            <div
                class="bg-base-50 border-t border-base-200 p-2 text-xs text-base-content/50 flex justify-between"
            >
                <span>{doc ? "Schema Validated" : "Loading..."}</span>
                <span
                    >{rootElement
                        ? rootElement.getAttribute("name")
                        : "-"}</span
                >
            </div>
        </div>

        <!-- Right Panel: Node Properties -->
        <div
            class="h-56 lg:h-auto w-full lg:w-80 bg-base-100 rounded-lg shadow border border-base-200 flex flex-col overflow-hidden flex-none"
        >
            <div
                class="p-3 border-b border-base-200 bg-base-50 font-bold text-xs uppercase tracking-wider"
            >
                Node Properties
            </div>
            <div class="p-4 flex-1 overflow-y-auto">
                {#if selectedNode}
                    <div class="flex flex-col gap-6">
                        <!-- Header -->
                        <div>
                            <div class="flex gap-2 mb-1">
                                <span class="badge badge-primary badge-sm"
                                    >ELEMENT</span
                                >
                                {#if selectedHelper.min === "1" && selectedHelper.max === "1"}
                                    <span class="badge badge-neutral badge-sm"
                                        >MANDATORY</span
                                    >
                                {:else}
                                    <span
                                        class="badge badge-ghost badge-sm border-base-300"
                                        >OPTIONAL</span
                                    >
                                {/if}
                            </div>
                            <h2 class="text-lg font-bold break-words">
                                {selectedNode.getAttribute("name") ||
                                    selectedNode.getAttribute("ref") ||
                                    "Anonymous"}
                            </h2>
                            <div
                                class="text-xs text-base-content/60 font-mono mt-1"
                            >
                                Type: <span class="text-primary"
                                    >{selectedHelper.typeName}</span
                                >
                            </div>
                        </div>

                        <!-- Description -->
                        <div class="prose prose-sm">
                            <h3
                                class="text-xs font-bold uppercase text-base-content/50 mb-2"
                            >
                                Description
                            </h3>
                            <p
                                class="text-sm leading-relaxed text-base-content/80"
                            >
                                {selectedHelper.doc}
                            </p>
                        </div>

                        <!-- Constraints -->
                        <div
                            class="p-3 bg-base-200/50 rounded-lg border border-base-200"
                        >
                            <h3
                                class="text-xs font-bold uppercase text-base-content/50 mb-3 flex items-center gap-2"
                            >
                                <svg
                                    xmlns="http://www.w3.org/2000/svg"
                                    class="h-4 w-4"
                                    fill="none"
                                    viewBox="0 0 24 24"
                                    stroke="currentColor"
                                    ><path
                                        stroke-linecap="round"
                                        stroke-linejoin="round"
                                        stroke-width="2"
                                        d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
                                    /><path
                                        stroke-linecap="round"
                                        stroke-linejoin="round"
                                        stroke-width="2"
                                        d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                                    /></svg
                                >
                                Technical Constraints
                            </h3>
                            <div class="grid grid-cols-2 gap-4 text-xs">
                                <div>
                                    <div class="opacity-50 mb-1">
                                        OCCURRENCES
                                    </div>
                                    <div class="font-mono font-bold">
                                        {selectedHelper.min} .. {selectedHelper.max ===
                                        "unbounded"
                                            ? "∞"
                                            : selectedHelper.max}
                                    </div>
                                </div>
                                <div>
                                    <div class="opacity-50 mb-1">BASE TYPE</div>
                                    <div class="font-mono truncate">
                                        {selectedHelper.typeName || "N/A"}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                {:else}
                    <div class="text-center mt-10 text-base-content/40">
                        <p class="text-sm">
                            Select an element to view details.
                        </p>
                    </div>
                {/if}
            </div>
        </div>
    </div>
</div>
