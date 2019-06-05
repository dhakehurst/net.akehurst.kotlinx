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