package net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection

class AAAA {
    var prop1 : String = "hello"
}

class BBBB {
    var prop1 : String = "B says hello"
}

class CCCC(val name: String, var count: Int = 0) {
    val description: String get() = "$name has $count"

    fun greet(greeting: String) = "$greeting $name"
    fun increment(by: Int = 1): Int {
        count += by
        return count
    }
}
/* TODO: enums not working, not sure why !!
enum class EEEE {
    red, blue, yellow, green
}
*/
sealed class SSSS {
    object red:SSSS()
    object blue:SSSS()
    object green:SSSS()
}
/*
object EEEE_external {
    val red = EEEE.red
    val blue = EEEE.blue
    val yellow = EEEE.yellow
    val green = EEEE.green
}

fun useEEEE() = EEEE.red

fun useSSSS() = SSSS.blue

 */