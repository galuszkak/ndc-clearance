<script lang="ts">
    import { untrack } from "svelte";
    import SchemaNode from "./SchemaNode.svelte";
    import {
        getDocumentation,
        collectElementsForTree,
        checkExpandability,
        matchesSearch,
        stripNamespacePrefix,
    } from "../utils/schema-helpers";
    import { DEFAULT_EXPAND_DEPTH, SEARCH } from "../utils/constants";
    import type { NodeSelectDetail } from "../utils/types";

    let {
        node,
        doc,
        depth = 0,
        path = "",
        definitions = {},
        searchQuery = "",
        targetPath = "",
        treeAction = "",
        treeActionVersion = 0,
        onmatch,
        onselect,
    }: {
        node: Element;
        doc: XMLDocument;
        depth?: number;
        path?: string;
        definitions?: Record<string, Element>;
        searchQuery?: string;
        targetPath?: string;
        treeAction?: "expand" | "collapse" | "";
        treeActionVersion?: number;
        onmatch?: () => void;
        onselect?: (detail: NodeSelectDetail) => void;
    } = $props();

    let expanded = $state((() => depth < DEFAULT_EXPAND_DEPTH)());
    let children: Element[] = $state([]);
    let typeDefinition: Element | null = $state(null);
    let hasResolved = $state(false);
    let elementRef: HTMLElement | undefined = $state(undefined);
    let isFlashed = $state(false);
    let appliedActionVersion = $state(-1);

    // Derived from node prop
    let name = $derived.by(() => {
        const raw =
            node.getAttribute("name") ||
            node.getAttribute("ref") ||
            "Anonymous";
        return stripNamespacePrefix(raw) || raw;
    });

    let typeNameOverride = $state<string | null>(null);
    let typeName = $derived(
        typeNameOverride ?? stripNamespacePrefix(node.getAttribute("type")),
    );

    let minOccurs = $derived(node.getAttribute("minOccurs") || "1");
    let maxOccurs = $derived(node.getAttribute("maxOccurs") || "1");

    let documentation = $derived(getDocumentation(node));

    // Search match (derived from props)
    let searchMatch = $derived(matchesSearch(name, documentation, searchQuery));
    let nameMatch = $derived(searchMatch.nameMatch);
    let docMatch = $derived(searchMatch.docMatch);
    let isMatch = $derived(nameMatch || docMatch);

    // Expandability (derived, not effect — avoids write-loop)
    let isExpandable = $derived.by(() => {
        if (hasResolved) {
            return children.length > 0;
        }
        const typeAttr = node.getAttribute("type");
        const refAttr = node.getAttribute("ref");
        const hasComplexChildren = Array.from(node.children).some((c) =>
            [
                "complexType",
                "simpleType",
                "sequence",
                "choice",
                "all",
                "complexContent",
            ].includes(c.localName),
        );
        const basicExpandable = !!(typeAttr || refAttr || hasComplexChildren);
        return basicExpandable
            ? checkExpandability(node, definitions)
            : false;
    });

    // Resolve Type / Ref
    function resolve() {
        if (typeDefinition && children.length > 0) return;

        children = [];

        if (node.getAttribute("ref")) {
            const refName = stripNamespacePrefix(node.getAttribute("ref"));
            const distinct = refName ? definitions[`element:${refName}`] : undefined;
            if (distinct) {
                const refType = distinct.getAttribute("type");
                if (refType) {
                    typeNameOverride = stripNamespacePrefix(refType);
                } else {
                    const inlineType = Array.from(distinct.children).find(
                        (c) => c.localName === "complexType",
                    );
                    if (inlineType) typeDefinition = inlineType;
                }
            }
        }

        if (typeName) {
            typeDefinition =
                definitions[`complexType:${typeName}`] ||
                definitions[`simpleType:${typeName}`];
        }

        if (!typeDefinition) {
            typeDefinition =
                Array.from(node.children).find(
                    (c) =>
                        c.localName === "complexType" ||
                        c.localName === "simpleType",
                ) || null;
        }

        if (node.localName === "choice") {
            children = collectElementsForTree(node, definitions);
        } else if (typeDefinition) {
            children = collectElementsForTree(typeDefinition, definitions);
        }
        hasResolved = true;
    }

    // Auto-resolve on search (untrack resolve to avoid circular deps)
    $effect(() => {
        if (searchQuery) untrack(() => resolve());
    });

    // Notify parent on match
    $effect(() => {
        if (isMatch) onmatch?.();
    });

    // Target Path Logic (Auto Expand & Scroll)
    $effect(() => {
        if (targetPath) {
            if (targetPath.startsWith(path) && targetPath !== path) {
                if (!untrack(() => expanded)) {
                    untrack(() => resolve());
                    expanded = true;
                }
            }
            if (targetPath === path) {
                let flashTimer: ReturnType<typeof setTimeout>;
                const scrollTimer = setTimeout(() => {
                    if (elementRef) {
                        elementRef.scrollIntoView({
                            behavior: "smooth",
                            block: "center",
                        });
                        onselect?.({ node, path, element: elementRef });
                        isFlashed = true;
                        flashTimer = setTimeout(() => (isFlashed = false), 2000);
                    }
                }, 100);
                return () => {
                    clearTimeout(scrollTimer);
                    clearTimeout(flashTimer);
                };
            }
        }
    });

    // Auto-resolve on expand (only track expanded, not children/resolve internals)
    $effect(() => {
        if (expanded && untrack(() => children.length === 0))
            untrack(() => resolve());
    });

    // Tree action handler (track version/action props, untrack local state)
    $effect(() => {
        const version = treeActionVersion;
        const action = treeAction;
        if (
            version > untrack(() => appliedActionVersion) &&
            action !== ""
        ) {
            if (action === "expand") {
                if (untrack(() => isExpandable)) {
                    expanded = true;
                    untrack(() => resolve());
                }
            } else if (action === "collapse") {
                expanded = depth < DEFAULT_EXPAND_DEPTH;
            }
            appliedActionVersion = version;
        }
    });

    // Auto-resolve choice
    $effect(() => {
        if (node.localName === "choice") untrack(() => resolve());
    });

    function handleChildMatch() {
        if (searchQuery.length >= SEARCH.MIN_QUERY_LENGTH) {
            expanded = true;
        }
        onmatch?.();
    }

    function handleClick(e: MouseEvent) {
        e.stopPropagation();
        onselect?.({ node, path, element: elementRef });
        if (!expanded) {
            expanded = true;
            resolve();
        }
    }

    function handleChildSelect(detail: NodeSelectDetail) {
        onselect?.(detail);
    }
</script>

<div class="ml-4 border-l border-base-300 pl-2">
    {#if node.localName === "choice"}
        <!-- Choice Container -->
        <div class="ml-0 pl-0 relative my-2">
            <div class="flex items-start">
                <div
                    class="flex-none flex flex-col items-center mr-2 pt-1 opacity-50 select-none cursor-help"
                    title="Mutually Exclusive: Only one of these can be chosen"
                >
                    <div
                        class="text-[10px] font-mono font-bold text-orange-600 bg-orange-100 px-1 rounded border border-orange-200 uppercase tracking-tighter"
                    >
                        One Of
                    </div>
                    <div
                        class="w-px h-full bg-orange-200/50 my-1 min-h-[1rem]"
                    ></div>
                </div>

                <div
                    class="flex-1 flex flex-col gap-1 border-l-2 border-orange-100 pl-2 -ml-2 py-1"
                >
                    {#each children as child, i (child.getAttribute("name") || child.getAttribute("ref") || i)}
                        <SchemaNode
                            node={child}
                            {doc}
                            {definitions}
                            {depth}
                            path={`${path}/${child.getAttribute("name") || stripNamespacePrefix(child.getAttribute("ref"))}`}
                            {searchQuery}
                            {targetPath}
                            {treeAction}
                            {treeActionVersion}
                            onmatch={handleChildMatch}
                            onselect={handleChildSelect}
                        />
                    {/each}
                </div>
            </div>
        </div>
    {:else}
        <!-- Standard Element Row -->
        <div
            class="flex items-center group cursor-pointer rounded -ml-2 px-2 py-1 transition-all duration-200 border border-transparent node-row
                hover:bg-base-200/50
                {isFlashed ? '!bg-yellow-200 !scale-[1.02] !shadow-md' : ''}"
            bind:this={elementRef}
            onclick={handleClick}
            role="button"
            tabindex="0"
            onkeydown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    e.stopPropagation();
                    onselect?.({ node, path, element: elementRef });
                    if (!expanded) {
                        expanded = true;
                        resolve();
                    }
                }
            }}
        >
            <!-- Expand Button / Icon -->
            <button
                class="btn btn-xs btn-ghost btn-square w-5 h-5 min-h-0 mr-1 p-0 flex items-center justify-center opacity-70 hover:opacity-100 hover:bg-base-200 rounded-md transition-colors"
                aria-expanded={isExpandable ? expanded : undefined}
                aria-label={isExpandable
                    ? (expanded ? "Collapse " : "Expand ") + name
                    : name}
                onclick={(e) => {
                    e.stopPropagation();
                    if (isExpandable) {
                        expanded = !expanded;
                        if (expanded) resolve();
                    }
                }}
            >
                {#if isExpandable}
                    {#if expanded}
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            class="h-3.5 w-3.5"
                            viewBox="0 0 20 20"
                            fill="currentColor"
                        >
                            <path
                                fill-rule="evenodd"
                                d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                clip-rule="evenodd"
                            />
                        </svg>
                    {:else}
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            class="h-3.5 w-3.5"
                            viewBox="0 0 20 20"
                            fill="currentColor"
                        >
                            <path
                                fill-rule="evenodd"
                                d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                                clip-rule="evenodd"
                            />
                        </svg>
                    {/if}
                {:else}
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-1.5 w-1.5 opacity-30"
                        viewBox="0 0 20 20"
                        fill="currentColor"
                    >
                        <circle cx="10" cy="10" r="10" />
                    </svg>
                {/if}
            </button>

            <!-- Icon -->
            <span
                class="mr-2 opacity-80 flex-none"
                title={typeName ? `Type: ${typeName}` : "Element"}
            >
                {#if name.endsWith("RQ") || name.endsWith("RS")}
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-4 w-4 text-purple-600"
                        viewBox="0 0 20 20"
                        fill="currentColor"
                        ><path
                            d="M7 3a1 1 0 000 2h6a1 1 0 100-2H7zM4 7a1 1 0 011-1h10a1 1 0 110 2H5a1 1 0 01-1-1zM2 11a2 2 0 012-2h12a2 2 0 012 2v4a2 2 0 01-2 2H4a2 2 0 01-2-2v-4z"
                        /></svg
                    >
                {:else if typeName}
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-4 w-4 text-blue-500"
                        viewBox="0 0 20 20"
                        fill="currentColor"
                        ><path
                            fill-rule="evenodd"
                            d="M2.5 3A1.5 1.5 0 001 4.5v.793c.026.009.051.02.076.032L7.674 8.51c.206.1.446.1.652 0l6.598-3.185A.755.755 0 0115 5.293V4.5A1.5 1.5 0 0013.5 3h-11zM15 6.913l-6.598 3.185a.755.755 0 01-.804 0L1 6.914V14.5a1.5 1.5 0 001.5 1.5h11a1.5 1.5 0 001.5-1.5V6.913z"
                            clip-rule="evenodd"
                        /></svg
                    >
                {:else}
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        class="h-4 w-4 text-orange-500"
                        viewBox="0 0 20 20"
                        fill="currentColor"
                        ><path
                            fill-rule="evenodd"
                            d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
                            clip-rule="evenodd"
                        /></svg
                    >
                {/if}
            </span>

            <!-- Label & Badges -->
            <div
                class="flex-1 min-w-0 flex items-center gap-2
                {nameMatch
                    ? 'bg-yellow-200 text-yellow-900 rounded px-1 -mx-1 ring-1 ring-yellow-400/50'
                    : ''}
                {docMatch
                    ? 'bg-info/20 text-info-content rounded px-1 -mx-1 ring-1 ring-info/30'
                    : ''}"
                title={docMatch
                    ? `Match in description: ...${documentation.substring(Math.max(0, documentation.toLowerCase().indexOf(searchQuery) - 30), Math.min(documentation.length, documentation.toLowerCase().indexOf(searchQuery) + 30))}...`
                    : path}
            >
                <span
                    class="font-medium text-sm text-base-content/90 truncate"
                    title={path}>{name}</span
                >
                {#if typeName}
                    <span
                        class="hidden group-hover:inline-block text-[10px] text-base-content/40 font-mono transition-opacity whitespace-nowrap bg-base-200 px-1 rounded"
                        >{typeName}</span
                    >
                {/if}

                <div class="flex items-center gap-1 ml-auto flex-none">
                    {#if minOccurs === "0"}
                        <span
                            class="badge badge-xs badge-ghost font-mono text-[9px] uppercase tracking-wider text-base-content/50 border-base-300"
                            >OPT</span
                        >
                    {:else}
                        <span
                            class="badge badge-xs badge-primary badge-outline font-mono text-[9px] uppercase tracking-wider font-bold"
                            >REQ</span
                        >
                    {/if}

                    {#if maxOccurs === "unbounded" || parseInt(maxOccurs) > 1}
                        <span
                            class="badge badge-xs badge-neutral font-mono text-[9px]"
                            title="List (Array)">LIST</span
                        >
                    {/if}
                </div>
            </div>
        </div>
        {#if expanded || searchQuery}
            <div
                class={!expanded && searchQuery
                    ? "hidden"
                    : "border-l border-base-200 ml-[0.35rem]"}
            >
                {#each children as child, i (child.getAttribute("name") || child.getAttribute("ref") || i)}
                    <SchemaNode
                        node={child}
                        {doc}
                        {definitions}
                        depth={depth + 1}
                        path={child.localName === "choice"
                            ? path
                            : `${path}/${child.getAttribute("name") || stripNamespacePrefix(child.getAttribute("ref"))}`}
                        {searchQuery}
                        {targetPath}
                        {treeAction}
                        {treeActionVersion}
                        onmatch={handleChildMatch}
                        onselect={handleChildSelect}
                    />
                {/each}
            </div>
        {/if}
    {/if}
</div>
