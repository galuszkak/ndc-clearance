<script lang="ts">
    import { onMount } from "svelte";
    import SchemaNode from "./SchemaNode.svelte";
    import { schemaStats } from "../utils/stores";
    import hljs from "highlight.js/lib/core";
    import xml from "highlight.js/lib/languages/xml";
    import "highlight.js/styles/github-dark.css";

    hljs.registerLanguage("xml", xml);

    export let schemaUrl: string;
    export let schemaFiles: { name: string; url: string }[] = [];

    let doc: XMLDocument | null = null;
    let rootElement: Element | null = null;
    let definitions: Record<string, Element> = {};
    let loading = true;
    let error = "";

    // Store content of all loaded files
    let loadedFiles: {
        name: string;
        content: string;
        url: string;
        doc: XMLDocument;
    }[] = [];
    let selectedFileIndex = 0;

    // Search & Interaction State
    let query = "";
    let selectedNode: Element | null = null;
    let selectedPath = "";
    let highlightedElement: HTMLElement | null = null;
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

    // Tabs & XML View
    let activeTab: "tree" | "xml" = "tree";
    let rawXml = ""; // Currently selected file content for view
    let highlightedXml = "";

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
        definitions = {};
        loadedFiles = [];

        try {
            // Determine files to load
            let filesToLoad =
                schemaFiles.length > 0
                    ? schemaFiles
                    : [
                          {
                              name: schemaUrl.split("/").pop() || "schema.xsd",
                              url: schemaUrl,
                          },
                      ];

            // Fetch all in parallel
            const promises = filesToLoad.map(async (file) => {
                const res = await fetch(file.url);
                if (!res.ok)
                    throw new Error(
                        `Failed to load ${file.name}: ${res.statusText}`,
                    );
                const text = await res.text();
                const parser = new DOMParser();
                const xmlDoc = parser.parseFromString(text, "application/xml");
                if (xmlDoc.querySelector("parsererror")) {
                    throw new Error(`XML Parse Error in ${file.name}`);
                }
                return {
                    name: file.name,
                    url: file.url,
                    content: text,
                    doc: xmlDoc,
                };
            });

            loadedFiles = await Promise.all(promises);

            // Aggregate definitions from ALL files
            for (const file of loadedFiles) {
                const schema = file.doc.documentElement;
                for (const child of Array.from(schema.children)) {
                    const name = child.getAttribute("name");
                    if (name) {
                        const key = `${child.localName}:${name}`;
                        // Last one wins if duplicates, but they should be unique in flattened set
                        definitions[key] = child;
                    }
                }
            }

            // Find Root Element
            // We look for the file that matches the schemaUrl (main file)
            // Or if not found, try to guess from the first file
            const mainFilename = schemaUrl.split("/").pop();
            const mainFile =
                loadedFiles.find((f) => f.name === mainFilename) ||
                loadedFiles[0];

            if (mainFile) {
                doc = mainFile.doc;
                const schema = doc.documentElement;
                const expectedRootName =
                    mainFilename?.replace(".xsd", "") || "";

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
            }

            if (rootElement) {
                const path = `/${rootElement.getAttribute("name")}`;
                handleSelect({ node: rootElement, path });
            }

            // Update stats (Aggregation across all)
            let totalElements = 0;
            let totalComplex = 0;
            let totalSimple = 0;

            loadedFiles.forEach((f) => {
                const children = Array.from(f.doc.documentElement.children);
                totalElements += children.filter(
                    (n) => n.localName === "element",
                ).length;
                totalComplex += children.filter(
                    (n) => n.localName === "complexType",
                ).length;
                totalSimple += children.filter(
                    (n) => n.localName === "simpleType",
                ).length;
            });

            schemaStats.set({
                elements: totalElements,
                complexTypes: totalComplex,
                simpleTypes: totalSimple,
            });

            // Set initial view for XML tab
            if (loadedFiles.length > 0) {
                rawXml = loadedFiles[0].content;
            }
        } catch (e: any) {
            error = e.message;
            console.error(e);
        } finally {
            loading = false;
        }
    }

    function handleSelect(detail: {
        node: Element;
        path: string;
        element?: HTMLElement;
    }) {
        selectedNode = detail.node;
        selectedPath = detail.path;

        // Manual Highlighting (Performance Optimization)
        if (highlightedElement) {
            highlightedElement.classList.remove(
                "bg-primary/10",
                "border-primary/20",
                "shadow-sm",
            );
            highlightedElement.classList.add(
                "border-transparent",
                "hover:bg-base-200/50",
            );
        }

        if (detail.element) {
            highlightedElement = detail.element;
            highlightedElement.classList.remove(
                "border-transparent",
                "hover:bg-base-200/50",
            );
            highlightedElement.classList.add(
                "bg-primary/10",
                "border-primary/20",
                "shadow-sm",
            );
        }

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
    interface SearchResult {
        name: string;
        path: string;
        type: string;
        doc: string;
        matchType: "name" | "doc";
        iconType: "message" | "typed" | "element";
        typeName?: string;
    }

    let searchResults: SearchResult[] = [];
    let isSearching = false;
    let targetPath = "";

    // Memoization for resolved elements
    const elementCache = new Map<string, Element[]>();

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
        // Try to cache based on type name if possible, or key from root
        let cacheKey = "";
        const typeName = root.getAttribute("name");

        // If it's a ComplexType or SimpleType definition, we can cache it
        if (
            ["complexType", "simpleType"].includes(root.localName) &&
            typeName
        ) {
            cacheKey = `${root.localName}:${typeName}`;
            if (elementCache.has(cacheKey)) {
                return elementCache.get(cacheKey)!;
            }
        }

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

        if (cacheKey) {
            elementCache.set(cacheKey, els);
        }
        return els;
    }

    async function performSearch() {
        if (!query || query.length < 1) {
            searchResults = [];
            return;
        }
        isSearching = true;
        searchResults = [];

        // Clear cache on new search to be safe or keep it?
        // Keeping it is better for performance across searches.

        // BFS Search
        const q = [
            {
                node: rootElement!,
                path: `/${rootElement!.getAttribute("name")}`,
            },
        ];
        let count = 0;
        let processedInChunk = 0;
        const CHUNK_SIZE = 50;

        while (q.length > 0 && count < 2000) {
            // Increased limit slightly due to better perf
            // Yield to main thread every CHUNK_SIZE nodes
            if (processedInChunk >= CHUNK_SIZE) {
                await new Promise((resolve) => setTimeout(resolve, 0));
                processedInChunk = 0;
                // If query changed while waiting, abort
                if (!isSearching) return;
            }

            // Limit search space
            const { node, path } = q.shift()!;
            count++;
            processedInChunk++;

            const name =
                node.getAttribute("name") ||
                node.getAttribute("ref")?.split(":").pop() ||
                "";

            // Get documentation
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
            const doc = getDocumentation(node);

            // Check match
            const lowerQuery = query.toLowerCase();
            const nameMatch = name.toLowerCase().includes(lowerQuery);
            const docMatch = doc.toLowerCase().includes(lowerQuery);

            let typeName = node.getAttribute("type");
            if (typeName && typeName.includes(":"))
                typeName = typeName.split(":")[1];

            // Resolve Ref for correct coloring
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

            let iconType: "message" | "typed" | "element" = "element";
            if (name.endsWith("RQ") || name.endsWith("RS")) {
                iconType = "message";
            } else if (typeName) {
                iconType = "typed";
            }

            if (nameMatch || docMatch) {
                searchResults.push({
                    name,
                    path,
                    type: "Element",
                    doc:
                        doc.substring(0, 100) + (doc.length > 100 ? "..." : ""),
                    matchType: nameMatch ? "name" : "doc",
                    iconType,
                    typeName: typeName || undefined,
                });
                // Force update svelte array
                searchResults = searchResults;
            }

            if (searchResults.length > 50) break; // Limit results

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

        // Sort results: Name matches first, then Doc matches
        searchResults.sort((a, b) => {
            if (a.matchType === "name" && b.matchType !== "name") return -1;
            if (a.matchType !== "name" && b.matchType === "name") return 1;
            return 0;
        });

        isSearching = false;
    }

    let searchDebounce: any;
    $: if (query) {
        clearTimeout(searchDebounce);
        // Abort previous search
        isSearching = false;
        searchDebounce = setTimeout(performSearch, 300);
    } else {
        searchResults = [];
        isSearching = false;
    }

    function selectResult(result: { path: string; node?: Element }) {
        targetPath = result.path;
        selectedPath = result.path;
        searchResults = []; // Close results
        activeTab = "tree"; // Switch to tree view if in XML
        // Don't clear query so user sees what they searched
    }

    $: if (schemaUrl) {
        loadSchema();
        elementCache.clear();
        rawXml = "";
        highlightedXml = "";
    }

    $: if (activeTab === "xml" && rawXml) {
        // Highlight in next tick to avoid blocking immediately
        highlightedXml = ""; // Clear first
        setTimeout(() => {
            try {
                highlightedXml = hljs.highlight(rawXml, {
                    language: "xml",
                }).value;
            } catch (e) {
                console.error("Highlight error", e);
                highlightedXml = rawXml
                    .replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;");
            }
        }, 10);
    }

    function selectFile(index: number) {
        selectedFileIndex = index;
        rawXml = loadedFiles[index].content;
    }
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
        <!-- Main Panel: Unified Card -->
        <div
            class="flex-1 flex flex-col bg-base-100 rounded-lg shadow border border-base-200 overflow-hidden"
        >
            <!-- Header: Search & Tabs -->
            <div
                class="flex items-center gap-3 p-2 border-b border-base-200 bg-base-50 relative z-20"
            >
                <!-- Search Bar -->
                <div class="relative flex-1">
                    <input
                        bind:this={searchInput}
                        type="text"
                        placeholder="Search elements..."
                        class="input input-sm input-bordered w-full pr-16 bg-base-100"
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

                    <!-- Search Results Dropdown -->
                    {#if searchResults.length > 0}
                        <div
                            class="absolute top-full left-0 right-0 bg-base-100 shadow-xl border border-base-200 rounded-b-lg max-h-[50vh] overflow-y-auto mt-1 z-50"
                        >
                            <ul class="menu menu-compact p-0">
                                {#each searchResults as result}
                                    <li>
                                        <button
                                            class="flex flex-col items-start gap-1 py-2 px-3 border-b border-base-100 last:border-0 hover:bg-base-200 transition-colors w-full"
                                            on:click={() =>
                                                selectResult(result)}
                                        >
                                            <div
                                                class="flex items-center gap-2 w-full text-left"
                                            >
                                                <!-- Icon -->
                                                <span
                                                    class="flex-none opacity-80"
                                                    title={result.typeName
                                                        ? `Type: ${result.typeName}`
                                                        : "Element"}
                                                >
                                                    {#if result.iconType === "message"}
                                                        <svg
                                                            xmlns="http://www.w3.org/2000/svg"
                                                            class="h-4 w-4 text-purple-600"
                                                            viewBox="0 0 20 20"
                                                            fill="currentColor"
                                                        >
                                                            <path
                                                                d="M7 3a1 1 0 000 2h6a1 1 0 100-2H7zM4 7a1 1 0 011-1h10a1 1 0 110 2H5a1 1 0 01-1-1zM2 11a2 2 0 012-2h12a2 2 0 012 2v4a2 2 0 01-2 2H4a2 2 0 01-2-2v-4z"
                                                            />
                                                        </svg>
                                                    {:else if result.iconType === "typed"}
                                                        <svg
                                                            xmlns="http://www.w3.org/2000/svg"
                                                            class="h-4 w-4 text-blue-500"
                                                            viewBox="0 0 20 20"
                                                            fill="currentColor"
                                                        >
                                                            <path
                                                                fill-rule="evenodd"
                                                                d="M2.5 3A1.5 1.5 0 001 4.5v.793c.026.009.051.02.076.032L7.674 8.51c.206.1.446.1.652 0l6.598-3.185A.755.755 0 0115 5.293V4.5A1.5 1.5 0 0013.5 3h-11zM15 6.913l-6.598 3.185a.755.755 0 01-.804 0L1 6.914V14.5a1.5 1.5 0 001.5 1.5h11a1.5 1.5 0 001.5-1.5V6.913z"
                                                                clip-rule="evenodd"
                                                            />
                                                        </svg>
                                                    {:else}
                                                        <svg
                                                            xmlns="http://www.w3.org/2000/svg"
                                                            class="h-4 w-4 text-orange-500"
                                                            viewBox="0 0 20 20"
                                                            fill="currentColor"
                                                        >
                                                            <path
                                                                fill-rule="evenodd"
                                                                d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
                                                                clip-rule="evenodd"
                                                            />
                                                        </svg>
                                                    {/if}
                                                </span>

                                                <!-- Name with Highlight -->
                                                <span
                                                    class="font-medium text-sm text-base-content/90 truncate {result.matchType ===
                                                    'name'
                                                        ? 'bg-yellow-200 text-yellow-900 rounded px-1 -mx-1 ring-1 ring-yellow-400/50'
                                                        : ''} {result.matchType ===
                                                    'doc'
                                                        ? 'bg-info/20 text-info-content rounded px-1 -mx-1 ring-1 ring-info/30'
                                                        : ''}"
                                                >
                                                    {result.name}
                                                </span>

                                                {#if result.typeName}
                                                    <span
                                                        class="text-[10px] text-base-content/40 font-mono bg-base-200 px-1 rounded ml-1 hidden sm:inline-block"
                                                    >
                                                        {result.typeName}
                                                    </span>
                                                {/if}
                                            </div>

                                            <span
                                                class="text-[10px] opacity-40 font-mono truncate w-full text-left pl-6"
                                                title={result.path}
                                                >{result.path}</span
                                            >

                                            {#if result.doc}
                                                <div
                                                    class="text-[10px] text-base-content/60 w-full text-left line-clamp-1 italic pl-6"
                                                >
                                                    {result.doc}
                                                </div>
                                            {/if}
                                        </button>
                                    </li>
                                {/each}
                            </ul>
                        </div>
                    {/if}
                </div>

                <!-- Tabs -->
                <div
                    role="tablist"
                    class="tabs tabs-boxed bg-base-200/50 p-1 rounded-lg flex-none"
                >
                    <button
                        role="tab"
                        class="tab tab-sm {activeTab === 'tree'
                            ? 'tab-active bg-base-100 shadow-sm'
                            : ''}"
                        on:click={() => (activeTab = "tree")}>Tree View</button
                    >
                    <button
                        role="tab"
                        class="tab tab-sm {activeTab === 'xml'
                            ? 'tab-active bg-base-100 shadow-sm'
                            : ''}"
                        on:click={() => (activeTab = "xml")}>XML Source</button
                    >
                </div>
            </div>

            <!-- Content Area -->
            <div class="flex-1 overflow-hidden relative flex flex-col">
                {#if activeTab === "tree"}
                    <!-- Tree View -->
                    <div
                        class="flex-1 overflow-auto p-4 relative bg-base-50/30"
                        style="content-visibility: auto; contain-intrinsic-size: 1px 500px;"
                    >
                        {#if loading}
                            <div class="flex justify-center p-10">
                                <span
                                    class="loading loading-spinner text-primary"
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
                                    searchQuery={query.length >= 3
                                        ? query.toLowerCase()
                                        : ""}
                                    {targetPath}
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
                    </div>

                    <!-- Floating auto-hide toolbar -->
                    <div
                        class="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 group/toolbar"
                    >
                        <!-- Invisible hover zone extending below toolbar -->
                        <div class="absolute -inset-4 -bottom-10"></div>
                        <div
                            class="flex items-center gap-2 p-1.5 bg-base-100/90 backdrop-blur shadow-xl border border-base-200 rounded-full opacity-0 group-hover/toolbar:opacity-100 transition-opacity duration-300"
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
                {:else}
                    <!-- XML Source View with File List -->
                    <div
                        class="flex h-full flex-col md:flex-row bg-base-100 rounded-lg shadow border border-base-200 overflow-hidden"
                    >
                        <!-- File List sidebar (visible on desktop) -->
                        {#if loadedFiles.length > 1}
                            <div
                                class="w-full md:w-48 border-b md:border-b-0 md:border-r border-base-200 bg-base-50 overflow-y-auto max-h-40 md:max-h-full flex-none"
                            >
                                <div
                                    class="p-2 text-xs font-bold opacity-50 uppercase tracking-wider sticky top-0 bg-base-50 z-10"
                                >
                                    Files
                                </div>
                                <div class="flex flex-col">
                                    {#each loadedFiles as file, idx}
                                        <button
                                            class="text-left px-3 py-2 text-xs font-mono truncate hover:bg-base-200 transition-colors {selectedFileIndex ===
                                            idx
                                                ? 'bg-primary/10 text-primary border-r-2 border-primary'
                                                : ''}"
                                            on:click={() => selectFile(idx)}
                                            title={file.name}
                                        >
                                            {file.name}
                                        </button>
                                    {/each}
                                </div>
                            </div>
                        {/if}

                        <div
                            class="flex-1 overflow-auto relative bg-[#0d1117] text-gray-300 pointer-events-auto select-text min-w-0"
                        >
                            {#if loading}
                                <div
                                    class="flex justify-center p-10 h-full items-center"
                                >
                                    <span
                                        class="loading loading-spinner text-primary"
                                    ></span>
                                </div>
                            {:else}
                                {#if highlightedXml}
                                    <pre
                                        class="m-0 p-4 font-mono text-sm leading-relaxed"><code
                                            class="language-xml"
                                            >{@html highlightedXml}</code
                                        ></pre>
                                {:else}
                                    <div
                                        class="flex justify-center p-10 h-full items-center text-base-content/50"
                                    >
                                        <span
                                            class="loading loading-spinner loading-sm mr-2"
                                        ></span> Highlighting...
                                    </div>
                                {/if}

                                <button
                                    class="btn btn-xs btn-circle btn-ghost absolute top-2 right-2 opacity-50 hover:opacity-100 transition-opacity bg-base-200 text-base-content"
                                    title="Copy XML"
                                    on:click={() =>
                                        navigator.clipboard.writeText(rawXml)}
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
                                            d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"
                                        /></svg
                                    >
                                </button>
                            {/if}
                        </div>
                    </div>
                {/if}
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
                                {#if selectedNode.localName === "choice"}
                                    <span
                                        class="badge badge-accent badge-sm uppercase"
                                        >CHOICE</span
                                    >
                                {:else if (selectedNode.getAttribute("name") || "").endsWith("RS") || (selectedNode.getAttribute("name") || "").endsWith("RQ")}
                                    <span
                                        class="badge badge-secondary badge-sm uppercase"
                                        >MESSAGE</span
                                    >
                                {:else}
                                    <span class="badge badge-primary badge-sm"
                                        >ELEMENT</span
                                    >
                                {/if}
                                {#if selectedHelper.min !== "0"}
                                    <span class="badge badge-primary badge-sm"
                                        >REQUIRED</span
                                    >
                                {:else}
                                    <span
                                        class="badge badge-ghost badge-sm border-base-300"
                                        >OPTIONAL</span
                                    >
                                {/if}
                                {#if selectedHelper.max === "unbounded" || parseInt(selectedHelper.max || "1") > 1}
                                    <span class="badge badge-neutral badge-sm"
                                        >LIST</span
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
