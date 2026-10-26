package net.akehurst.kotlinx.utils

import org.w3c.dom.Document

inline fun objectJS(init: dynamic.() -> Unit): JsAny {
    val o:dynamic = js("{}")
    init(o)
    return o
}

inline fun <T:JsAny> objectJSTyped(init: T.() -> Unit): T {
    val o:dynamic =  js("{}")
    init(o)
    return o as T
}

fun <T : JsAny> T.setJsAny(key: String, value: JsAny): T {
    val self = this
    js("self[key] = value")
    return self
}

/**
 * Returns the value stored under [key], typed as [V]; there is no runtime check that the value is a [V].
 */
fun <V : JsAny?> JsAny.getJsAny(key: String): V {
    val self = this
    val value = js("self[key]")
    return value
}

fun Document.adoptCss(css: String) {
    // replaceSync applies the CSS straight away and throws if it can't be parsed (it ignores @import rules).
    // Create the sheet at call time. A Kotlin subclass of CSSStyleSheet would be defined when the module loads,
    // which crashes outside a browser (e.g. node), where CSSStyleSheet does not exist.
    val sheet: dynamic = js("new CSSStyleSheet()")
    sheet.replaceSync(css)
    this.asDynamic().adoptedStyleSheets.push(sheet)
}