package net.akehurst.kotlinx.utils

import org.w3c.dom.Document
import org.w3c.dom.css.CSSStyleSheet

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

fun <T : JsAny> T.getJsAny(key: String): T {
    val self = this
    val value = js("self[key]")
    return value
}

fun Document.adoptCss(css: String) {
    val sheet = object : CSSStyleSheet() {}
    sheet.asDynamic().replace(css)
    this.asDynamic().adoptedStyleSheets.push(sheet)
}