<script lang="ts">
    import {
        SvelteFlow,
        Background,
        Controls,
        Position,
        type Node,
        type Edge,
    } from "@xyflow/svelte";
    import "@xyflow/svelte/dist/style.css";
    import dagre from "@dagrejs/dagre";
    import type { FlowRecord } from "../utils/types";

    let {
        flow,
        onNodeClick,
    }: {
        flow: FlowRecord;
        onNodeClick: (stepId: string) => void;
    } = $props();

    let nodes = $state<Node[]>([]);
    let edges = $state<Edge[]>([]);

    function formatLabel(message: string, order: number): string {
        const short = message.replace(/^IATA_/, "");
        return `${order}. ${short}`;
    }

    function estimateNodeWidth(label: string): number {
        // ~7px per char at 12px font + 24px padding
        return Math.max(160, Math.min(280, label.length * 7.5 + 24));
    }

    function getLayoutedElements(
        requestNodes: Node[],
        requestEdges: Edge[],
        direction = "TB",
    ) {
        const dagreGraph = new dagre.graphlib.Graph();
        dagreGraph.setDefaultEdgeLabel(() => ({}));

        const isHorizontal = direction === "LR";
        dagreGraph.setGraph({ rankdir: direction, nodesep: 30, ranksep: 50 });

        requestNodes.forEach((node) => {
            const w = estimateNodeWidth(node.data.label as string);
            dagreGraph.setNode(node.id, { width: w, height: 50 });
        });

        requestEdges.forEach((edge) => {
            dagreGraph.setEdge(edge.source, edge.target);
        });

        dagre.layout(dagreGraph);

        const newNodes = requestNodes.map((node) => {
            const nodeWithPosition = dagreGraph.node(node.id);
            const w = estimateNodeWidth(node.data.label as string);
            return {
                ...node,
                targetPosition: isHorizontal ? Position.Left : Position.Top,
                sourcePosition: isHorizontal ? Position.Right : Position.Bottom,
                position: {
                    x: nodeWithPosition.x - w / 2,
                    y: nodeWithPosition.y - 25,
                },
            };
        });

        return { nodes: newNodes, edges: requestEdges };
    }

    $effect(() => {
        if (flow && flow.steps) {
            const initialNodes: Node[] = flow.steps.map((step) => {
                const label = formatLabel(step.message, step.order);
                const w = estimateNodeWidth(label);
                return {
                    id: step.step_id,
                    type: "default",
                    data: { label },
                    position: { x: 0, y: 0 },
                    style: `
                        background: ${step.optional ? "#f3f4f6" : "#fff"};
                        border: 1px solid ${step.optional ? "#d1d5db" : "#777"};
                        border-radius: 8px;
                        padding: 8px 12px;
                        width: ${w}px;
                        text-align: center;
                        font-size: 12px;
                        font-family: inherit;
                        box-shadow: 0 2px 4px -1px rgb(0 0 0 / 0.1);
                        cursor: pointer;
                        color: #1f2937;
                    `,
                };
            });

            const initialEdges: Edge[] = [];
            flow.steps.forEach((step) => {
                if (step.next && step.next.length > 0) {
                    step.next.forEach((nextId) => {
                        initialEdges.push({
                            id: `${step.step_id}-${nextId}`,
                            source: step.step_id,
                            target: nextId,
                            animated: true,
                            style: "stroke: #888;",
                        });
                    });
                }
            });

            const layouted = getLayoutedElements(initialNodes, initialEdges);
            nodes = layouted.nodes;
            edges = layouted.edges;
        }
    });
</script>

<div style="height: 100%; width: 100%;">
    <SvelteFlow
        bind:nodes
        bind:edges
        onnodeclick={(event) => onNodeClick(event.node.id)}
        fitView
    >
        <Controls />
        <Background />
    </SvelteFlow>
</div>
