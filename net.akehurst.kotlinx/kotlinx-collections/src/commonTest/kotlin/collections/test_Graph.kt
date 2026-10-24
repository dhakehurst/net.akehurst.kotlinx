/*
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import kotlin.test.*

class test_GraphFromFunctions {

    private fun n(id: String) = GraphNodeId(id)
    private fun e(id: String) = GraphEdgeId(id)

    // a --e1--> b --e2--> c,  a --e3--> c
    private val nodeIds = listOf(n("a"), n("b"), n("c"))
    private val edgeEnds = linkedMapOf(
        e("e1") to listOf(n("a"), n("b")),
        e("e2") to listOf(n("b"), n("c")),
        e("e3") to listOf(n("a"), n("c")),
    )

    private fun graph(): Graph = GraphFromFunctions(
        GraphFunctions<String, String>(
            nodeIds = { nodeIds.asSequence() },
            node = { it.value },
            incoming = { nid -> edgeEnds.filter { it.value.last() == nid }.keys.asSequence() },
            outgoing = { nid -> edgeEnds.filter { it.value.first() == nid }.keys.asSequence() },
            edgeIds = { edgeEnds.keys.asSequence() },
            edge = { it.value },
            ends = { eid -> edgeEnds[eid]!!.asSequence() },
        )
    )

    private fun Graph.node(id: String) = nodes.first { it.id == n(id) }
    private fun Graph.edge(id: String) = edges.first { it.id == e(id) }

    @Test
    fun nodes_and_edges() {
        val g = graph()
        assertEquals(nodeIds, g.nodes.map { it.id })
        assertEquals(edgeEnds.keys.toList(), g.edges.map { it.id })
    }

    @Test
    fun incoming_and_outgoing() {
        val g = graph()
        assertEquals(emptyList(), g.node("a").incoming.map { it.id })
        assertEquals(listOf(e("e1"), e("e3")), g.node("a").outgoing.map { it.id })
        assertEquals(listOf(e("e1")), g.node("b").incoming.map { it.id })
        assertEquals(listOf(e("e2")), g.node("b").outgoing.map { it.id })
        assertEquals(listOf(e("e2"), e("e3")), g.node("c").incoming.map { it.id })
        assertEquals(emptyList(), g.node("c").outgoing.map { it.id })
    }

    @Test
    fun edge_ends_source_and_target() {
        val g = graph()
        val e1 = g.edge("e1")
        assertEquals(listOf(n("a"), n("b")), e1.ends.map { it.id })
        assertEquals(n("a"), e1.source.id)
        assertEquals(n("b"), e1.target.id)
    }

    @Test
    fun same_node_object_is_reached_by_every_path() {
        val g = graph()
        val b = g.node("b")
        assertSame(b, g.edge("e1").target)
        assertSame(b, g.edge("e2").source)
        assertSame(g.edge("e2"), b.outgoing.single())
    }

    @Test
    fun functions_are_called_lazily() {
        var nodeIdsCalls = 0
        var edgeIdsCalls = 0
        val g = GraphFromFunctions(
            GraphFunctions<String, String>(
                nodeIds = { nodeIdsCalls++; nodeIds.asSequence() },
                node = { it.value },
                incoming = { emptySequence() },
                outgoing = { emptySequence() },
                edgeIds = { edgeIdsCalls++; emptySequence() },
                edge = { it.value },
                ends = { emptySequence() },
            )
        )
        assertEquals(0, nodeIdsCalls)
        g.nodes
        g.nodes
        assertEquals(1, nodeIdsCalls)
        assertEquals(0, edgeIdsCalls)
    }
}

class test_GraphSimple {

    @Test
    fun build_simple_graph() {
        val g = GraphSimple("g")
        val a = GraphNodeSimple(GraphNodeId("a"))
        val b = GraphNodeSimple(GraphNodeId("b"))
        val ab = GraphEdgeSimple(GraphEdgeId("ab"))
        ab.ends.add(a)
        ab.ends.add(b)
        a.outgoing.add(ab)
        b.incoming.add(ab)
        g.nodes.add(a)
        g.nodes.add(b)
        g.edges.add(ab)

        assertEquals(2, g.nodes.size)
        assertSame(a, ab.source)
        assertSame(b, ab.target)
        assertSame(ab, a.outgoing.single())
        assertSame(ab, b.incoming.single())
    }

    @Test
    fun simple_elements_are_equal_by_id() {
        assertEquals(GraphNodeSimple(GraphNodeId("a")), GraphNodeSimple(GraphNodeId("a")))
        assertEquals(GraphEdgeSimple(GraphEdgeId("e")), GraphEdgeSimple(GraphEdgeId("e")))
        assertEquals(GraphSimple("g"), GraphSimple("g"))
        assertNotEquals(GraphNodeSimple(GraphNodeId("a")), GraphNodeSimple(GraphNodeId("b")))
    }

    @Test
    fun ids_are_equal_by_value() {
        assertEquals(GraphNodeId("x"), GraphNodeId("x"))
        assertEquals(GraphEdgeId("x"), GraphEdgeId("x"))
        assertNotEquals(GraphNodeId("x"), GraphNodeId("y"))
    }
}
