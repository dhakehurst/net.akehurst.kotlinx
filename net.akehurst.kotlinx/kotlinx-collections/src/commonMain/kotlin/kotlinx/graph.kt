/*
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.akehurst.kotlinx.collections

import kotlin.jvm.JvmInline

@JvmInline
value class GraphNodeId(val value: String)

@JvmInline
value class GraphEdgeId(val value: String)

interface Graph {
    val nodes: Iterable<GraphNode>
    val edges: Iterable<GraphEdge>
}

interface GraphNode {
    val id: GraphNodeId
    val incoming: Iterable<GraphEdge>
    val outgoing: Iterable<GraphEdge>
}

interface GraphEdge {
    val id: GraphEdgeId
    val ends: Iterable<GraphNode>

    val source:GraphNode get() = ends.first()
    val target:GraphNode get() = ends.last()
}


class GraphFunctions<NT : Any, ET : Any>(
    /** returns a sequence of node ids */
    val nodeIds: () -> Sequence<GraphNodeId>,
    /** returns the node for the given Id */
    val node: (nodeId: GraphNodeId) -> NT,
    /** return sequence of incoming edge Ids */
    val incoming: (nodeId: GraphNodeId) -> Sequence<GraphEdgeId>,
    /** return sequence of outgoing edge Ids */
    val outgoing: (nodeId: GraphNodeId) -> Sequence<GraphEdgeId>,
    /** returns a sequence of edge ids */
    val edgeIds: () -> Sequence<GraphEdgeId>,
    /** returns the edge for the given Id */
    val edge: (edgeId: GraphEdgeId) -> ET,
    /** returns a sequence of nodeId ends */
    val ends: (edgeId: GraphEdgeId) -> Sequence<GraphNodeId>
)

class GraphFromFunctions<NT : Any, ET : Any>(
    val functions: GraphFunctions<NT, ET>
) : Graph {

    private val _nodes: Map<GraphNodeId, GraphNode> = lazyMap { nodeId ->
        object : GraphNode {
            override val id: GraphNodeId = nodeId
            override val incoming: Iterable<GraphEdge> by lazy {
                val edgeIds = functions.incoming.invoke(nodeId)
                edgeIds.mapNotNull { _edges[it] }.toList()
            }
            override val outgoing: Iterable<GraphEdge> by lazy {
                val edgeIds = functions.outgoing.invoke(nodeId)
                edgeIds.mapNotNull { _edges[it] }.toList()
            }
        }
    }

    private val _edges: Map<GraphEdgeId, GraphEdge> = lazyMap { edgeId ->
        object : GraphEdge {
            override val id: GraphEdgeId = edgeId
            override val ends: Iterable<GraphNode> by lazy {
                val nodeIds = functions.ends.invoke(edgeId)
                nodeIds.mapNotNull { _nodes[it] }.toList()
            }
        }
    }

    override val nodes: Iterable<GraphNode> by lazy {
        val ids = functions.nodeIds.invoke()
        ids.mapNotNull { nodeId ->
            _nodes[nodeId]
        }.toList()
    }

    override val edges: Iterable<GraphEdge> by lazy {
        val ids = functions.edgeIds.invoke()
        ids.mapNotNull { nodeId ->
            _edges[nodeId]
        }.toList()
    }
}

data class GraphSimple(
    val graphId: String
) : Graph {
    override val nodes = mutableListOf<GraphNode>()
    override val edges = mutableListOf<GraphEdge>()
}

data class GraphNodeSimple(
    override val id: GraphNodeId
) : GraphNode {
    override val incoming = mutableListOf<GraphEdge>()
    override val outgoing = mutableListOf<GraphEdge>()
}

data class GraphEdgeSimple(
    override val id: GraphEdgeId
) : GraphEdge {
    override val ends = mutableListOf<GraphNode>()
}