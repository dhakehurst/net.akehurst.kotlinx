package kotlinx.utils

fun <IN, OUT> cached(initializer: (IN) -> OUT) = CachedValue(initializer)

class CachedValue<IN, OUT>(
   var initializer: (IN) -> OUT
) {

    private val _cache = mutableMapOf<IN, OUT>()

    operator fun get(key: IN): OUT? = when {
        _cache.containsKey(key) -> _cache[key]
        else -> {
            val out = initializer.invoke(key)
            _cache[key] = out
            out
        }
    }

    operator fun set(key: IN, value: OUT) {
        this._cache[key] = value
    }

    fun reset(key: IN) {
        this._cache.remove(key)
    }

    fun resetAll() {
        this._cache.clear()
    }

}

