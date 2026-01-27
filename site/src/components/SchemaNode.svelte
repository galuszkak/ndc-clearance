<script lang="ts">
    export let node: Element;
    export let doc: XMLDocument;
    export let depth = 0;
    export let path = "";
    export let definitions: Record<string, Element> = {}; // Map of name -> definition node
    export let searchQuery = "";
    export let targetPath = "";
    export let treeAction: "expand" | "collapse" | "" = "";
    export let treeActionVersion = 0;
    export let onmatch: (() => void) | undefined = undefined;
    export let onselect:
        | ((detail: {
              node: Element;
              path: string;
              element?: HTMLElement;
          }) => void)
        | undefined = undefined;

    let expanded = depth < 2; // Auto-expand top levels
    let children: Element[] = [];
    let typeDefinition: Element | null = null;
    let validation = "";
    let documentation = "";
    let hasResolved = false;

    // Deterministic Expandability Check
    // We check if the node has a type that is likely complex, or has specific children
    let isExpandable = false;
    $: {
        // Re-evaluate if node changes (unlikely) or on init
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

        // Initial guess
        const basicExpandable = !!(typeAttr || refAttr || hasComplexChildren);

        if (basicExpandable) {
            // Perform deeper check against definitions
            isExpandable = checkExpandability(node, definitions);
        } else {
            isExpandable = false;
        }
    }

    // Helper to peek if a node results in children
    function checkExpandability(
        root: Element,
        defs: Record<string, Element>,
        visited = new Set<string>(),
    ): boolean {
        let typeName = root.getAttribute("type");
        if (typeName && typeName.includes(":"))
            typeName = typeName.split(":")[1];

        // 1. Check Ref
        if (root.getAttribute("ref")) {
            const refName = root.getAttribute("ref")!.split(":").pop()!;
            // Avoid infinite loops if recursive
            if (visited.has(refName)) return true; // Assume expandable if recursion
            visited.add(refName);

            const distinct = defs[`element:${refName}`];
            if (distinct) return checkExpandability(distinct, defs, visited);
        }

        // 2. Check Type Definition
        if (typeName) {
            if (defs[`simpleType:${typeName}`]) return false;
            const complex = defs[`complexType:${typeName}`];
            if (complex) return hasElementChildren(complex, defs);
        }

        // 3. Inline Complex
        const inline = Array.from(root.children).find(
            (c) => c.localName === "complexType",
        );
        if (inline) return hasElementChildren(inline, defs);

        return false;
    }

    function hasElementChildren(
        root: Element,
        defs: Record<string, Element>,
    ): boolean {
        // BFS/DFS to find first 'element'
        // We don't need full collection, just boolean
        const q = [root];
        let limit = 0;
        while (q.length > 0 && limit < 50) {
            // Safety limit
            const curr = q.shift()!;
            limit++;

            for (const child of Array.from(curr.children)) {
                if (child.localName === "element") return true;
                if (["sequence", "choice", "all"].includes(child.localName)) {
                    q.push(child);
                } else if (child.localName === "complexContent") {
                    for (const cc of Array.from(child.children)) {
                        if (
                            cc.localName === "extension" ||
                            cc.localName === "restriction"
                        ) {
                            // Check base
                            const base = cc
                                .getAttribute("base")
                                ?.split(":")
                                .pop();
                            if (base) {
                                // If base has children, we have children
                                if (
                                    defs[`complexType:${base}`] &&
                                    hasElementChildren(
                                        defs[`complexType:${base}`],
                                        defs,
                                    )
                                )
                                    return true;
                            }
                            q.push(cc);
                        }
                    }
                }
            }
        }
        return false;
    }

    $: if (hasResolved) {
        isExpandable = children.length > 0;
    }

    let elementRef: HTMLElement;
    let isFlashed = false;

    // Extract basic info
    let name =
        node.getAttribute("name") || node.getAttribute("ref") || "Anonymous";
    if (name.includes(":")) name = name.split(":")[1]; // distinct prefix

    let typeName = node.getAttribute("type");
    if (typeName && typeName.includes(":")) typeName = typeName.split(":")[1];

    let minOccurs = node.getAttribute("minOccurs") || "1";
    let maxOccurs = node.getAttribute("maxOccurs") || "1";

    // Documentation
    // Documentation helper
    function getDocumentation(el: Element) {
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
    }
    documentation = getDocumentation(node);

    // Resolve Type / Ref
    function resolve() {
        // If already resolved, skip (unless we want to refresh?)
        if (typeDefinition && children.length > 0) return;

        children = [];

        // If it's a ref, find the element definition
        if (node.getAttribute("ref")) {
            const refName = node.getAttribute("ref")!.split(":").pop()!;
            const distinct = definitions[`element:${refName}`];
            if (distinct) {
                const refType = distinct.getAttribute("type");
                if (refType) {
                    typeName = refType.split(":").pop() ?? null;
                } else {
                    const inlineType = Array.from(distinct.children).find(
                        (c) => c.localName === "complexType",
                    );
                    if (inlineType) typeDefinition = inlineType;
                }
                // Also, the referenced element might have children structure directly if no type
                if (!typeDefinition && !refType) {
                    // Check relative structure
                    // For simplified NDC, use typeName usually.
                }
            }
        }

        if (typeName) {
            // Look for complexType or simpleType
            typeDefinition =
                definitions[`complexType:${typeName}`] ||
                definitions[`simpleType:${typeName}`];
        }

        // If no type name, check for inline complexType/simpleType
        if (!typeDefinition) {
            typeDefinition =
                Array.from(node.children).find(
                    (c) =>
                        c.localName === "complexType" ||
                        c.localName === "simpleType",
                ) || null;
        }

        if (node.localName === "choice") {
            children = collectElements(node);
        } else if (typeDefinition) {
            children = collectElements(typeDefinition);
        }
        hasResolved = true;
    }

    // Flatten structure: sequence, all, extension (but NOT choice)
    function collectElements(root: Element): Element[] {
        const els: Element[] = [];
        for (const child of Array.from(root.children)) {
            if (child.localName === "element") {
                els.push(child);
            } else if (child.localName === "choice") {
                // Keep choice as a distinct node to render
                els.push(child);
            } else if (["sequence", "all"].includes(child.localName)) {
                els.push(...collectElements(child));
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
                                els.push(...collectElements(baseDef));
                            }
                        }
                        els.push(...collectElements(cc));
                    }
                }
            } else if (
                child.localName === "complexType" ||
                child.localName === "simpleType"
            ) {
                els.push(...collectElements(child));
            }
        }
        return els;
    }

    // Reactive Search Logic
    // Reactive Search Logic
    let nameMatch = false;
    let docMatch = false;

    $: {
        if (searchQuery) {
            const lowerQuery = searchQuery;
            nameMatch = name.toLowerCase().includes(lowerQuery);
            docMatch =
                !nameMatch &&
                !!documentation &&
                documentation.toLowerCase().includes(lowerQuery);
        } else {
            nameMatch = false;
            docMatch = false;
        }
    }

    $: isMatch = nameMatch || docMatch;

    $: if (searchQuery) {
        resolve(); // Ensure children are known
    }

    $: if (isMatch) {
        onmatch?.();
    }

    // Target Path Logic (Auto Expand & Scroll)
    $: if (targetPath) {
        // If we are on the path to the target, expand!
        if (targetPath.startsWith(path) && targetPath !== path) {
            if (!expanded) {
                resolve(); // Resolve first to ensure we know if we have children
                expanded = true;
            }
        }
        // If we ARE the target, scroll into view
        if (targetPath === path) {
            setTimeout(() => {
                if (elementRef) {
                    elementRef.scrollIntoView({
                        behavior: "smooth",
                        block: "center",
                    });
                    // Highlight visually
                    onselect?.({ node, path, element: elementRef });
                    // Trigger Flash
                    isFlashed = true;
                    setTimeout(() => (isFlashed = false), 2000);
                }
            }, 100);
        }
    }

    function handleChildMatch() {
        // Performance: Only auto-expand if query is long enough to be specific
        if (searchQuery.length >= 3) {
            expanded = true;
        }
        onmatch?.();
    }

    function handleClick(e: MouseEvent) {
        e.stopPropagation();
        onselect?.({ node, path, element: elementRef });
        // Also toggle expansion if it has children
        if (!expanded) {
            expanded = true;
            resolve();
        }
    }

    // Forward child selection
    function handleChildSelect(detail: {
        node: Element;
        path: string;
        element?: HTMLElement;
    }) {
        onselect?.(detail);
    }

    $: if (expanded && children.length === 0) resolve();

    let appliedActionVersion = -1;
    $: if (treeActionVersion > appliedActionVersion && treeAction !== "") {
        if (treeAction === "expand") {
            if (isExpandable) {
                expanded = true;
                resolve();
            }
        } else if (treeAction === "collapse") {
            expanded = depth < 2;
        }
        appliedActionVersion = treeActionVersion;
    }

    $: if (node.localName === "choice") {
        resolve();
    }
</script>

<div class="ml-4 border-l border-base-300 pl-2">
    {#if node.localName === "choice"}
        <!-- Choice Container -->
        <div class="ml-0 pl-0 relative my-2">
            <!-- Visual Bracket/Label -->
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
                    {#each children as child}
                        <svelte:self
                            node={child}
                            {doc}
                            {definitions}
                            {depth}
                            path={`${path}/${child.getAttribute("name") || child.getAttribute("ref")?.split(":").pop()}`}
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
            on:click={handleClick}
            role="button"
            tabindex="0"
            on:keydown={(e) => e.key === "Enter" && handleClick(e as any)}
        >
            <!-- Expand Button / Icon -->
            <button
                class="btn btn-xs btn-ghost btn-square w-5 h-5 min-h-0 mr-1 p-0 flex items-center justify-center opacity-70 hover:opacity-100 hover:bg-base-200 rounded-md transition-colors"
                on:click|stopPropagation={() => {
                    if (isExpandable) {
                        expanded = !expanded;
                        if (expanded) resolve();
                    }
                }}
            >
                {#if isExpandable}
                    {#if expanded}
                        <!-- Chevron Down -->
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
                        <!-- Chevron Right -->
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
                    <!-- Leaf Dot -->
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
                    <!-- Message Icon -->
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
                    <!-- Typed Element Icon -->
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
                    <!-- Generic Element Icon -->
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
                    <!-- Cardinality Badges -->
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
                {#each children as child}
                    <svelte:self
                        node={child}
                        {doc}
                        {definitions}
                        depth={depth + 1}
                        path={child.localName === "choice"
                            ? path
                            : `${path}/${child.getAttribute("name") || child.getAttribute("ref")?.split(":").pop()}`}
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
