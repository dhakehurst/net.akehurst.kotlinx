package net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection

class AAAA {
    var prop1 : String = "hello"
}

class BBBB {
    var prop1 : String = "B says hello"
}

enum class EEEE {
    red, blue, yellow, green
}

sealed class SSSS {
    object red:SSSS()
    object blue:SSSS()
    object green:SSSS()
}

object EEEE_external {
    val red = EEEE.red
    val blue = EEEE.blue
    val yellow = EEEE.yellow
    val green = EEEE.green
}

fun useEEEE() = EEEE.red

fun useSSSS() = SSSS.blue