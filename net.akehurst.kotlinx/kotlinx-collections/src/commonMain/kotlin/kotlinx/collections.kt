package net.akehurst.kotlinx.collections

fun <T> Set<T>.transitveClosure(function: (T) -> Set<T>): Set<T> {
    var result:MutableSet<T> = this.toMutableSet()
    var newThings:MutableSet<T> = this.toMutableSet()
    var newStuff = true
    while (newStuff) {
        val temp = newThings.toSet()
        newThings.clear()
        for (nt:T in temp) {
            val s:Set<T> = function.invoke(nt)
            newThings.addAll(s)
        }
        newThings.removeAll(result)
        newStuff = result.addAll(newThings)
    }
    return result
}

class Stack<T> {

    private val list = mutableListOf<T>()

    fun push(element:T) {
        list.add(element)
    }

    fun pop() : T {
        val v = list.last()
        list.removeAt(list.size-1)
        return v
    }

    fun peek() :T {
        return list.last()
    }
}