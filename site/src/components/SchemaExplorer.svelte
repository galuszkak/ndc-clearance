<script lang="ts">
    import SchemaNode from "./SchemaNode.svelte";
    import NodePropertiesPanel from "./NodePropertiesPanel.svelte";
    import SearchPanel from "./SearchPanel.svelte";
    import TreeToolbar from "./TreeToolbar.svelte";
    import XmlSourceView from "./XmlSourceView.svelte";
    import { schemaStats } from "../utils/stores";
    import {
        getDocumentation,
        resolveTypeName,
        resolveChildren,
        matchesSearch,
        getIconType,
        stripNamespacePrefix,
    } from "../utils/schema-helpers";
    import { SEARCH, ZOOM } from "../utils/constants";
    import type {
        SearchResult,
        LoadedSchemaFile,
        SchemaFileRef,
        NodeSelectDetail,
        SelectedNodeHelper,
    } from "../utils/types";
    import { hljs } from "../utils/highlight";

    let {
        schemaUrl,
        schemaFiles = [],
    }: {
        schemaUrl: string;
        schemaFiles?: SchemaFileRef[];
    } = $props();

    let doc: XMLDocument | null = $state(null);
    let rootElement: Element | null = $state(null);
    let definitions: Record<string, Element> = $state({});
    let loading = $state(true);
    let error = $state("");

    // Store content of all loaded files
    let loadedFiles: LoadedSchemaFile[] = $state([]);
    let selectedFileIndex = $state(0);

    // Search & Interaction State
    let query = $state("");
    let selectedNode: Element | null = $state(null);
    let selectedPath = $state("");
    let highlightedElement: HTMLElement | null = $state(null);
    let selectedHelper: SelectedNodeHelper = $state({});

    let treeAction: "expand" | "collapse" | "" = $state("");
    let treeActionVersion = $state(0);
    let zoom = $state(ZOOM.DEFAULT);

    // Tabs & XML View
    let activeTab: "tree" | "xml" = $state("tree");
    let rawXml = $state("");
    let highlightedXml = $state("");

    let searchInput: HTMLInputElement | undefined = $state(undefined);

    function handleKeyDown(e: KeyboardEvent) {
        if ((e.ctrlKey || e.metaKey) && e.key === "f") {
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
            let filesToLoad =
                schemaFiles.length > 0
                    ? schemaFiles
                    : [
                          {
                              name: schemaUrl.split("/").pop() || "schema.xsd",
                              url: schemaUrl,
                          },
                      ];

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
                    const defName = child.getAttribute("name");
                    if (defName) {
                        const key = `${child.localName}:${defName}`;
                        definitions[key] = child;
                    }
                }
            }

            // Find Root Element
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
                const rootPath = `/${rootElement.getAttribute("name")}`;
                handleSelect({ node: rootElement, path: rootPath });
            }

            // Update stats
            let totalElements = 0;
            let totalComplex = 0;
            let totalSimple = 0;

            loadedFiles.forEach((f) => {
                const fileChildren = Array.from(f.doc.documentElement.children);
                totalElements += fileChildren.filter(
                    (n) => n.localName === "element",
                ).length;
                totalComplex += fileChildren.filter(
                    (n) => n.localName === "complexType",
                ).length;
                totalSimple += fileChildren.filter(
                    (n) => n.localName === "simpleType",
                ).length;
            });

            schemaStats.set({
                elements: totalElements,
                complexTypes: totalComplex,
                simpleTypes: totalSimple,
            });

            if (loadedFiles.length > 0) {
                rawXml = loadedFiles[0].content;
            }
        } catch (e: unknown) {
            error = e instanceof Error ? e.message : String(e);
        } finally {
            loading = false;
        }
    }

    function handleSelect(detail: NodeSelectDetail) {
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

        const selectNode = detail.node;

        const typeName = resolveTypeName(selectNode, definitions);

        let docText = getDocumentation(selectNode);

        selectedHelper = {
            typeName:
                typeName ||
                (Array.from(selectNode.children).find(
                    (c) => c.localName === "complexType",
                )
                    ? "Anonymous ComplexType"
                    : "Anonymous SimpleType"),
            min: selectNode.getAttribute("minOccurs") || "1",
            max: selectNode.getAttribute("maxOccurs") || "1",
            doc: docText || "No description available.",
            regex: "",
        };
    }

    function onNodeSelect(detail: NodeSelectDetail) {
        handleSelect(detail);
    }

    // Search Logic
    let searchResults: SearchResult[] = $state([]);
    let isSearching = $state(false);
    let targetPath = $state("");

    // Memoization for resolved elements
    const elementCache = new Map<string, Element[]>();

    async function performSearch() {
        if (!query || query.length < 1) {
            searchResults = [];
            return;
        }
        isSearching = true;

        const results: SearchResult[] = [];
        const q = [
            {
                node: rootElement!,
                path: `/${rootElement!.getAttribute("name")}`,
            },
        ];
        let count = 0;
        let processedInChunk = 0;
        const CHUNK_SIZE = SEARCH.CHUNK_SIZE;

        while (q.length > 0 && count < SEARCH.MAX_NODES) {
            if (processedInChunk >= CHUNK_SIZE) {
                await new Promise((resolve) => setTimeout(resolve, 0));
                processedInChunk = 0;
                if (!isSearching) return;
            }

            const { node: searchNode, path: searchPath } = q.shift()!;
            count++;
            processedInChunk++;

            const nodeName =
                searchNode.getAttribute("name") ||
                stripNamespacePrefix(searchNode.getAttribute("ref")) ||
                "";

            const nodeDoc = getDocumentation(searchNode);
            const nodeTypeName = resolveTypeName(searchNode, definitions);
            const { nameMatch, docMatch } = matchesSearch(nodeName, nodeDoc, query);
            const iconType = getIconType(nodeName, nodeTypeName);

            if (nameMatch || docMatch) {
                results.push({
                    name: nodeName,
                    path: searchPath,
                    type: "Element",
                    doc:
                        nodeDoc.substring(0, 100) +
                        (nodeDoc.length > 100 ? "..." : ""),
                    matchType: nameMatch ? "name" : "doc",
                    iconType,
                    typeName: nodeTypeName || undefined,
                });
            }

            if (results.length > SEARCH.MAX_RESULTS) break;

            const nodeChildren = resolveChildren(searchNode, definitions, elementCache);
            for (const child of nodeChildren) {
                const childName =
                    child.getAttribute("name") ||
                    stripNamespacePrefix(child.getAttribute("ref"));
                if (childName) {
                    if (!searchPath.includes(`/${childName}/`)) {
                        q.push({
                            node: child,
                            path: `${searchPath}/${childName}`,
                        });
                    }
                }
            }
        }

        results.sort((a, b) => {
            if (a.matchType === "name" && b.matchType !== "name") return -1;
            if (a.matchType !== "name" && b.matchType === "name") return 1;
            return 0;
        });

        searchResults = results;
        isSearching = false;
    }

    // Search debounce effect
    $effect(() => {
        if (query) {
            isSearching = false;
            const timer = setTimeout(performSearch, SEARCH.DEBOUNCE_MS);
            return () => clearTimeout(timer);
        } else {
            searchResults = [];
            isSearching = false;
        }
    });

    function selectResult(result: { path: string; node?: Element }) {
        targetPath = result.path;
        selectedPath = result.path;
        searchResults = [];
        activeTab = "tree";
    }

    // Load schema when URL changes
    $effect(() => {
        if (schemaUrl) {
            loadSchema();
            elementCache.clear();
            rawXml = "";
            highlightedXml = "";
        }
    });

    // Highlight XML when switching to XML tab
    $effect(() => {
        if (activeTab === "xml" && rawXml) {
            highlightedXml = "";
            const timer = setTimeout(() => {
                try {
                    highlightedXml = hljs.highlight(rawXml, {
                        language: "xml",
                    }).value;
                } catch {
                    highlightedXml = rawXml
                        .replace(/</g, "&lt;")
                        .replace(/>/g, "&gt;");
                }
            }, 10);
            return () => clearTimeout(timer);
        }
    });

    function selectFile(index: number) {
        selectedFileIndex = index;
        rawXml = loadedFiles[index].content;
    }

    function handleExpandAll() {
        treeAction = "expand";
        treeActionVersion++;
        query = "";
    }

    function handleCollapseAll() {
        treeAction = "collapse";
        treeActionVersion++;
        query = "";
    }
</script>

<svelte:window onkeydown={handleKeyDown} />

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
                <SearchPanel
                    bind:query
                    {searchResults}
                    {isSearching}
                    bind:searchInput
                    onSelectResult={selectResult}
                />

                <!-- Tabs -->
                <div
                    role="tablist"
                    class="tabs tabs-boxed bg-base-200/50 p-1 rounded-lg flex-none"
                >
                    <button
                        role="tab"
                        aria-selected={activeTab === "tree"}
                        aria-controls="tree-panel"
                        class="tab tab-sm {activeTab === 'tree'
                            ? 'tab-active bg-base-100 shadow-sm'
                            : ''}"
                        onclick={() => (activeTab = "tree")}>Tree View</button
                    >
                    <button
                        role="tab"
                        aria-selected={activeTab === "xml"}
                        aria-controls="xml-panel"
                        class="tab tab-sm {activeTab === 'xml'
                            ? 'tab-active bg-base-100 shadow-sm'
                            : ''}"
                        onclick={() => (activeTab = "xml")}>XML Source</button
                    >
                </div>
            </div>

            <!-- Content Area -->
            <div class="flex-1 overflow-hidden relative flex flex-col">
                {#if activeTab === "tree"}
                    <!-- Tree View -->
                    <div
                        id="tree-panel"
                        role="tabpanel"
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
                                <button class="btn btn-sm btn-ghost" onclick={() => loadSchema()}>Retry</button>
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
                                    searchQuery={query.length >= SEARCH.MIN_QUERY_LENGTH
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

                    <TreeToolbar
                        bind:zoom
                        onExpandAll={handleExpandAll}
                        onCollapseAll={handleCollapseAll}
                    />

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
                    <div id="xml-panel" role="tabpanel" class="flex-1 overflow-hidden">
                        <XmlSourceView
                            {loadedFiles}
                            {selectedFileIndex}
                            {rawXml}
                            {highlightedXml}
                            {loading}
                            onSelectFile={selectFile}
                        />
                    </div>
                {/if}
            </div>
        </div>

        <!-- Right Panel: Node Properties -->
        <NodePropertiesPanel {selectedNode} {selectedHelper} />
    </div>
</div>
