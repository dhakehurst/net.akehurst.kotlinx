# kotlinx-collections

Multiplatform collections that the Kotlin stdlib doesn't have: ordered sets, managed collections that
check their elements and report changes, derived (computed) collections, stacks, queues, binary heaps,
lazy and cached maps, and some graph and tree helpers.

```kotlin
val set = mutableOrderedSetOf("b", "a")
set += listOf("c", "a")
set[2]                                  // "c": insertion order, no duplicates

val owned = ManagedList<Any>("owned", Shape::class, onAdded = { println("added $it") })
owned.add(circle)                       // prints "added ..."
owned.add("text")                       // throws IllegalArgumentException: not a Shape
```

## Contents

- [Setup](#setup)
- [Ordered sets](#ordered-sets)
- [Managed collections](#managed-collections)
- [Derived collections](#derived-collections)
- [Stacks and queues](#stacks-and-queues)
- [Binary heaps](#binary-heaps)
- [Lazy and cached maps](#lazy-and-cached-maps)
- [Other collections](#other-collections)
- [Functions](#functions)
- [Graphs and trees](#graphs-and-trees)
- [Issues](#issues)

## Setup

The module is published to Maven Central under the group `net.akehurst.kotlinx`. It depends on
`kotlinx-utils` (for `Reference`).

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("net.akehurst.kotlinx:kotlinx-collections:<version>")
            }
        }
    }
}
```

Everything is in package `net.akehurst.kotlinx.collections`, except [issues](#issues).

## Ordered sets

An `OrderedSet<E>` is a `Set` that keeps insertion order and can be indexed with `get(index)`.
`MutableOrderedSet<E>` adds the `MutableSet` operations.

```kotlin
val a = orderedSetOf(1, 2, 3)
val b = mutableOrderedSetOf<Int>()
val c = listOf(3, 1, 3).toOrderedSet()   // [3, 1]
val d = a + 4                            // a new OrderedSet [1, 2, 3, 4]
emptyOrderedSet<String>()
```

Equality:

- With another `OrderedSet`: the same elements in the same order.
- With a plain `Set`: the same elements, order ignored. This keeps `equals` symmetric, because a plain
  set's own `equals` ignores order, but it means equality is not transitive across ordered and plain sets.
- `hashCode` is the plain `Set` hash code, so equal sets always have equal hash codes.

## Managed collections

Managed collections check the type of every element at runtime, which catches elements that got past
the compiler through generic upcasts, and call hooks when elements are added or removed. They are
meant for the properties of model objects.

| Class | Kind | Notes |
|---|---|---|
| `ManagedCollection(name, expectedType, onAdded, onRemoved)` | bag | duplicates allowed |
| `ManagedList(...)` | `MutableList` | `set(i, x)` calls `onRemoved` for the old element, then `onAdded` for the new one |
| `ManagedSet(...)` | `MutableSet` | `onAdded` is not called for an element that was already there |
| `ManagedOrderedSet(...)` | `MutableOrderedSet` | insertion order |
| `ManagedReferenceCollection`, `ManagedReferenceList`, `ManagedReferenceSet`, `ManagedReferenceOrderedSet` | the same, of `Reference<Any, T>` | check the type of what each reference resolves to; unresolved references are accepted |

```kotlin
val members = ManagedReferenceList<Person>("members", Person::class)
members.add(ResolvableReferenceDefault<Any, Person>("alice", alice))
```

- A wrong type throws `IllegalArgumentException`, naming the collection by its `name`.
- Removing through an iterator also calls `onRemoved`.
- The storage behind a managed collection is only created on the first add, because most of them stay
  empty (for example, one for each property of every model object). While empty it has size 0 and an
  empty iterator, and index access throws `IndexOutOfBoundsException`.
- The classes are `open`; a subclass can override `validateType`. `name` is `protected`.

Put `ResolvableReference`s in reference collections, not `MutableReference`s: equality follows the
reference key, so a key that changes would break a set (see the
[kotlinx-utils README](../kotlinx-utils/README.md#references)).

## Derived collections

Derived collections are read-only, and their content is computed by a `derivation` function whenever
they are used. They are the collection counterparts of `Derived` in `kotlinx-utils`.

| Class | Kind |
|---|---|
| `DerivedCollection(name, expectedType, derivation)` | `Collection` |
| `DerivedList(...)` | `List` |
| `DerivedSet(...)` | `Set` |
| `DerivedOrderedSet(...)` | `OrderedSet` |

```kotlin
class Department(val staff: List<Person>) {
    val managers = DerivedList<Person>("managers", Person::class) { staff.filter { it.isManager } }
}
```

- Each operation (`size`, `get`, `iterator`, `contains`, ...) computes the content once, so iterating
  does not recompute per element. Two operations compute it twice.
- Every computed element is type-checked. A wrong type is a bug in the derivation, so it throws
  `IllegalStateException`, naming the collection.

## Stacks and queues

| Class | Use |
|---|---|
| `Stack<T>` | An immutable stack: `push`, `pushAll` and `pop` return a new stack and leave this one unchanged. O(1), and stacks share their lower elements. `pop()` returns a `PopResult(item, stack)` |
| `MutableStack<T>`, `mutableStackOf(...)` | A mutable stack. `peek(n)` and `pop(n)` give the top `n` elements bottom first, in the order they were pushed |
| `MutableQueue<T>`, `mutableQueueOf(...)` | A simple queue: `enqueue`, `dequeue`, `peek` |
| `FifoQueue<E>`, `fifoQueue(...)` | A doubly linked queue that can add and remove at both ends |

`elements` is bottom first (top of stack last) on both stacks. On `MutableStack` it is a copy.

```kotlin
val s1 = Stack<Int>().push(1).push(2)
val (top, s2) = s1.pop().let { it.item to it.stack }   // top = 2, s1 is unchanged

val ms = mutableStackOf(1, 2, 3)
ms.pop(2)                                               // [2, 3]
```

Before kotlinx 2.4.0, `MutableStack.pop(n)` returned the elements top first (`[3, 2]`).

## Binary heaps

`BinaryHeap<K, V>` is a priority queue of values by key: `binaryHeapMin()` and `binaryHeapMax()` for
`Comparable` keys, or `binaryHeap(comparator)`. `heap[key] = value` inserts, `peekRoot` gives the value
with the smallest (or largest) key, and `extractRoot()` removes it. `BinaryHeapFifo` (`binaryHeapFifoMin()`,
`binaryHeapFifoMax()`, `binaryHeapFifo(comparator)`) keeps values with equal keys in insertion order.

## Lazy and cached maps

| Class | Use |
|---|---|
| `LazyMap(accessor)`, `lazyMap { ... }` | A `Map` whose values are computed by `accessor` the first time each key is read |
| `LazyMapNotNull`, `LazyMutableMapNotNull` (`lazyMapNotNull`, `lazyMutableMapNotNull`) | The same, with non-null values. Can be used with `by` |
| `MapNotNull`, `MutableMapNotNull`, `mutableMapNonNullOf()` | A map whose `get` never returns null: a missing key throws `NoSuchElementException` |
| `CachedValue`, `cachedByKey { ... }` | A cache of computed values per key, with `reset(key)` and `resetAll()` |
| `CachedValueSuspend`, `cachedByKeySuspend { ... }` | The same, computed by a `suspend` function. A key being computed is computed once; other callers wait for it. If it fails, nothing is cached. Not thread-safe |

The equality of the lazy maps compares only the values computed so far; `equals` never computes a value.

## Other collections

| Class | Use |
|---|---|
| `ListSeparated<E, I, S>`, `MutableListSeparated` | A list of items separated by separators, e.g. the parts of `a, b, c`: `items`, `separators` and all `elements` |
| `LazyArray`, `lazyArray(size) { i -> ... }` | A fixed-size array whose elements are computed on first access |
| `IndexFunction`, `indexFunction { i -> ... }` | A function readable with `[index]` |
| `MapIntTo<V>` | A hash map with `Int` keys, stored without boxing |
| `MutableMap2IntKeys<V>` | A map keyed by two `Int`s, without creating key objects |
| `Tuple4` | A data class of four values |

## Functions

| Function | Purpose |
|---|---|
| `Set<T>.transitiveClosure(accumulate) { next }` | Everything reachable by repeatedly applying `next`. With `accumulate = false`, only what is reached in the last step. `inline`, so `next` can call `suspend` functions |
| `List<T>.transitiveClosure { next }` | The same for lists; each element appears once, in the order it is first found |
| `Collection<E>.topologicalSort(comparator)` | A stable partial-order sort (Kahn's algorithm). `compare(a, b) > 0` means `a` must come after `b`; 0 means unrelated. Fails on a cycle |
| `Map.clone()`, `Map.deepClone()`, `Collection.deepClone()` | Copies; `deepClone` also copies nested collections |
| `List.indexOfOrNull(e)`, `IntArray.including(v)` | Small helpers |
| `ListExt.mutable`, `CollectionExt.mutable`, `SetExt.mutable`, `OrderedSetExt.mutable` | Cast a read-only collection to its mutable type |

## Graphs and trees

`Graph`, `GraphNode`, `GraphEdge` (with `GraphNodeId`, `GraphEdgeId`) and `Tree`, `TreeNode` are small
interfaces for showing data as a graph or tree. `GraphFunctions` / `GraphFromFunctions` and
`TreeFunctions` build them from functions over your own objects, and `GraphSimple` is a plain data
implementation.

## Issues

Package `net.akehurst.kotlinx.issues.api` has `Issue<L>` (a `kind`, an optional `location` of type
`L`, a `message` and `data`), `IssueKind` (`ERROR`, `WARNING`, `INFORMATION`) and `IssueCollection<L>`.
Package `net.akehurst.kotlinx.issues.ram` has `IssueHolder<L>`, which collects them:

```kotlin
val issues = IssueHolder<Location>()
issues.error(loc, "Unknown type 'Foo'")
issues.warn(null, "Deprecated syntax")
if (issues.errors.isNotEmpty()) println(issues)
```
