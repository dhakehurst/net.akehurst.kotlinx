package net.akehurst.kotlinx.text

import kotlin.test.*

class RegexFromGlobTest {

    companion object {

        data class TestData(
            val globPattern: String,
            val separator: Char,
            val value: String,
            val shouldMatch: Boolean,
            val shouldThrow: Boolean = false
        )

        val testData = listOf(
            TestData("/", '/', "", false),
            TestData("a/b", '/', "a/b", true),
            TestData("a[/]b", '/', "a/b", false),
            TestData("/", '/', "/", true),
            TestData(".", '.', "", false),
            TestData("a.b", '.', "a.b", true),
            TestData("a[.]b", '.', "a.b", false),
            TestData(".", '.', ".", true),

            // literal
            TestData("", '/', "", true),
            TestData("", '/', "a", false),
            TestData("", '/', "a/b", false),
            TestData("", '/', "aaa/bbb/ccc", false),

            TestData("a", '/', "", false),
            TestData("a", '/', "a", true),
            TestData("a", '/', "a/b", false),
            TestData("a", '/', "aa/bb", false),
            TestData("a", '/', "aaa/bbb", false),
            TestData("a", '/', "a/b/c", false),
            TestData("a", '/', "aa/bb/cc", false),
            TestData("a", '/', "aaa/bbb/ccc", false),

            TestData("aa/bb", '/', "", false),
            TestData("aa/bb", '/', "a", false),
            TestData("aa/bb", '/', "a/b", false),
            TestData("aa/bb", '/', "aa/bb", true),
            TestData("aa/bb", '/', "aaa/bbb", false),
            TestData("aa/bb", '/', "a/b/c", false),
            TestData("aa/bb", '/', "aa/bb/cc", false),
            TestData("aa/bb", '/', "aaa/bbb/ccc", false),

            TestData("aaa/bbb/ccc", '/', "", false),
            TestData("aaa/bbb/ccc", '/', "a", false),
            TestData("aaa/bbb/ccc", '/', "a/b", false),
            TestData("aaa/bbb/ccc", '/', "aa/bb", false),
            TestData("aaa/bbb/ccc", '/', "aaa/bbb", false),
            TestData("aaa/bbb/ccc", '/', "a/b/c", false),
            TestData("aaa/bbb/ccc", '/', "aa/bb/cc", false),
            TestData("aaa/bbb/ccc", '/', "aaa/bbb/ccc", true),

            //patterns '?'
            TestData("?", '/', "", false),
            TestData("?", '/', "a", true),
            TestData("?", '/', "a/b", false),
            TestData("?", '/', "aa/bb", false),
            TestData("?", '/', "aaa/bbb", false),
            TestData("?", '/', "a/b/c", false),
            TestData("?", '/', "aa/bb/cc", false),
            TestData("?", '/', "aaa/bbb/ccc", false),

            TestData("???", '/', "a/b", false),

            TestData("??/??", '/', "", false),
            TestData("??/??", '/', "a", false),
            TestData("??/??", '/', "a/b", false),
            TestData("??/??", '/', "aa/bb", true),
            TestData("??/??", '/', "aaa/bbb", false),
            TestData("??/??", '/', "a/b/c", false),
            TestData("??/??", '/', "aa/bb/cc", false),
            TestData("??/??", '/', "aaa/bbb/ccc", false),

            TestData("???/???/???", '/', "", false),
            TestData("???/???/???", '/', "a", false),
            TestData("???/???/???", '/', "a/b", false),
            TestData("???/???/???", '/', "aa/bb", false),
            TestData("???/???/???", '/', "aaa/bbb", false),
            TestData("???/???/???", '/', "a/b/c", false),
            TestData("???/???/???", '/', "aa/bb/cc", false),
            TestData("???/???/???", '/', "aaa/bbb/ccc", true),

            //patterns '*'
            TestData("*", '/', "", true),
            TestData("*", '/', "a", true),
            TestData("*", '/', "aa", true),
            TestData("*", '/', "aaa", true),
            TestData("*", '/', "a/b", false),
            TestData("*", '/', "aa/bb", false),
            TestData("*", '/', "aaa/bbb", false),
            TestData("*", '/', "a/b/c", false),
            TestData("*", '/', "aa/bb/cc", false),
            TestData("*", '/', "aaa/bbb/ccc", false),

            TestData("a*", '/', "", false),
            TestData("a*", '/', "a", true),
            TestData("a*", '/', "aa", true),
            TestData("a*", '/', "aaa", true),
            TestData("a*", '/', "a/b", false),
            TestData("a*", '/', "aa/bb", false),
            TestData("a*", '/', "aaa/bbb", false),
            TestData("a*", '/', "a/b/c", false),
            TestData("a*", '/', "aa/bb/cc", false),
            TestData("a*", '/', "aaa/bbb/ccc", false),

            TestData("*/*", '/', "", false),
            TestData("*/*", '/', "a", false),
            TestData("*/*", '/', "a/b", true),
            TestData("*/*", '/', "aa/bb", true),
            TestData("*/*", '/', "aaa/bbb", true),
            TestData("*/*", '/', "a/b/c", false),
            TestData("*/*", '/', "aa/bb/cc", false),
            TestData("*/*", '/', "aaa/bbb/ccc", false),

            TestData("a/*", '/', "", false),
            TestData("a/*", '/', "a", false),
            TestData("a/*", '/', "a/b", true),
            TestData("a/*", '/', "aa/bb", false),
            TestData("a/*", '/', "aaa/bbb", false),
            TestData("a/*", '/', "a/b/c", false),
            TestData("a/*", '/', "aa/bb/cc", false),
            TestData("a/*", '/', "aaa/bbb/ccc", false),

            TestData("a*/b*", '/', "", false),
            TestData("a*/b*", '/', "a", false),
            TestData("a*/b*", '/', "a/b", true),
            TestData("a*/b*", '/', "aa/bb", true),
            TestData("a*/b*", '/', "aaa/bbb", true),
            TestData("a*/b*", '/', "a/b/c", false),
            TestData("a*/b*", '/', "aa/bb/cc", false),
            TestData("a*/b*", '/', "aaa/bbb/ccc", false),

            TestData("*/*/*", '/', "", false),
            TestData("*/*/*", '/', "a", false),
            TestData("*/*/*", '/', "a/b", false),
            TestData("*/*/*", '/', "aa/bb", false),
            TestData("*/*/*", '/', "aaa/bbb", false),
            TestData("*/*/*", '/', "a/b/c", true),
            TestData("*/*/*", '/', "aa/bb/cc", true),
            TestData("*/*/*", '/', "aaa/bbb/ccc", true),

            TestData("a*/b*/*", '/', "", false),
            TestData("a*/b*/*", '/', "a", false),
            TestData("a*/b*/*", '/', "a/b", false),
            TestData("a*/b*/*", '/', "aa/bb", false),
            TestData("a*/b*/*", '/', "aaa/bbb", false),
            TestData("a*/b*/*", '/', "a/b/c", true),
            TestData("a*/b*/*", '/', "aa/bb/cc", true),
            TestData("a*/b*/*", '/', "aaa/bbb/ccc", true),

            TestData("a*/b*/c*", '/', "", false),
            TestData("a*/b*/c*", '/', "a", false),
            TestData("a*/b*/c*", '/', "a/b", false),
            TestData("a*/b*/c*", '/', "aa/bb", false),
            TestData("a*/b*/c*", '/', "aaa/bbb", false),
            TestData("a*/b*/c*", '/', "a/b/c", true),
            TestData("a*/b*/c*", '/', "aa/bb/cc", true),
            TestData("a*/b*/c*", '/', "aaa/bbb/ccc", true),

            //patterns '**'
            TestData("**", '/', "", true),
            TestData("**", '/', "a", true),
            TestData("**", '/', "a/b", true),
            TestData("**", '/', "aa/bb", true),
            TestData("**", '/', "aaa/bbb", true),
            TestData("**", '/', "a/b/c", true),
            TestData("**", '/', "aa/bb/cc", true),
            TestData("**", '/', "aaa/bbb/ccc", true),

            TestData("**/b", '/', "", false),
            TestData("**/b", '/', "a", false),
            TestData("**/b", '/', "a/b", true),
            TestData("**/b", '/', "aa/bb", false),
            TestData("**/b", '/', "aaa/bbb", false),
            TestData("**/b", '/', "a/b/c", false),
            TestData("**/b", '/', "aa/bb/cc", false),
            TestData("**/b", '/', "aaa/bbb/ccc", false),

            TestData("**/b*/**", '/', "", false),
            TestData("**/b*/**", '/', "a", false),
            TestData("**/b*/**", '/', "a/b", false),
            TestData("**/b*/**", '/', "aa/bb", false),
            TestData("**/b*/**", '/', "aaa/bbb", false),
            TestData("**/b*/**", '/', "a/b/c", true),
            TestData("**/b*/**", '/', "aa/bb/cc", true),
            TestData("**/b*/**", '/', "aaa/bbb/ccc", true),

            TestData("**/cc", '/', "", false),
            TestData("**/cc", '/', "a", false),
            TestData("**/cc", '/', "a/b", false),
            TestData("**/cc", '/', "aa/bb", false),
            TestData("**/cc", '/', "aaa/bbb", false),
            TestData("**/cc", '/', "a/b/c", false),
            TestData("**/cc", '/', "aa/bb/cc", true),
            TestData("**/cc", '/', "aaa/bbb/ccc", false),

            // character classes
            TestData("[a-c]", '/', "", false),
            TestData("[a-c]", '/', "a", true),
            TestData("[a-c]", '/', "b", true),
            TestData("[a-c]", '/', "c", true),
            TestData("[a-c]", '/', "d", false),
            TestData("[a-c]", '/', "z", false),
            TestData("[bcd]", '/', "a", false),
            TestData("[bcd]", '/', "b", true),
            TestData("[bcd]", '/', "c", true),
            TestData("[bcd]", '/', "d", true),
            TestData("[bcd]", '/', "e", false),
            TestData("[a-c]", '/', "1", false),
            TestData("[!a-c]", '/', "1", true),
            TestData("[!a-c]", '/', "a", false),
            TestData("[!a-c]", '/', "b", false),
            TestData("[!a-c]", '/', "c", false),
            TestData("a[!a]c", '/', "abc", true),
            TestData("a[!a]c", '/', "a/b/c", false),
            TestData("[a-z][1-9]", '/', "z", false),
            TestData("[a-z][1-9]", '/', "a1", true),
            TestData("[a-c][a-c][a-c]", '/', "a1", false),
            TestData("[a-c][a-c][a-c]", '/', "abc", true),
            TestData("[a-c][a-c][a-c]", '/', "ab3", false),
            TestData("[a-c][a-c][a-c]", '/', "a1c", false),
            TestData("[a-c][abc][!xyz]/[def][def][def]/[a-z][ghi][g-i]", '/', "", false),
            TestData("[a-c][abc][!xyz]/[def][def][def]/[a-z][ghi][g-i]", '/', "a/d/g", false),
            TestData("[a-c][abc][!xyz]/[def][def][def]/[a-z][ghi][g-i]", '/', "aaa/bbb/ccc", false),
            TestData("[a-c][abc][!xyz]/[def][def][def]/[a-z][ghi][g-i]", '/', "abc/def/g", false),
            TestData("[a-c][abc][!xyz]/[def][def][def]/[a-z][ghi][g-i]", '/', "abc/d/ghi", false),
            TestData("[a-c][abc][!xyz]/[def][def][def]/[!1234][ghi][g-i]", '/', "abc/def/ghi", true),

            // groups
            TestData("{a,b,c}", '/', "a", true),
            TestData("{a,b,c}", '/', "b", true),
            TestData("{a,b,c}", '/', "c", true),
            TestData("{a,b,c}", '/', "z", false),
            TestData("a{[!1-9],z}{x,b,[abc]}/**", '/', "", false),
            TestData("a{[!1-9],z}{x,b,[abc]}/**", '/', "a", false),
            TestData("a{[!1-9],z}{x,b,[abc]}/**", '/', "a/b", false),
            TestData("a{[!1-9],z}{x,b,[abc]}/**", '/', "ab/bc", false),
            TestData("a{[!1-9],z}{x,b,[abc]}/**", '/', "abc/def", true),
            TestData("a{b}{c}/**", '/', "a/b/c", false),
            TestData("a{[a-c],z}{x,b,[abc]}/**", '/', "a/b/c", false),
            TestData("a{[!1-9],z}{x,b,[abc]}/**", '/', "a/b/c", false),
            TestData("a{[!1-9]}{x,b}/**", '/', "a/b/c", false),
            TestData("a{[!1-9],z}{x,b,[abc]}/**", '/', "ab/cd/ef", false),
            TestData("a{[!1-9],z}{x,b,[abc]}/**", '/', "abc/def/ghi", true),
            TestData("**/{d,e,f}e{f,[f]}/**", '/', "abc/def/ghi", true),

            // escape special chars
            TestData("a{", '/', "a{", false, true),
            TestData("a\\{", '/', "a{", true),
            TestData("a\\*", '/', "a*", true),
            TestData("a\\*\\*/b/c", '/', "a**/b/c", true),
            TestData("a?b", '/', "a?b", true),
            TestData("a\\?b", '/', "a?b", true),
            TestData("a[b", '/', "a[b", false, true),
            TestData("a\\[b", '/', "a[b", true),
            TestData("a]b", '/', "a]b", false),
            TestData("a\\]b", '/', "a]b", true),
        )

    }

    @Test
    // Test currently fails on JVM for some inputs (and JS all inputs) due to issue KT-48382
    fun testGlobs() {
        val failed = mutableListOf<TestData>()
        for (td in testData) {
            if (td.shouldThrow) {
                assertFailsWith {
                    td.globPattern.toRegexFromGlob(td.separator)
                }
            } else {
                if (td.globPattern.toRegexFromGlob(td.separator).matches(td.value) == td.shouldMatch) {
                    //pass
                } else {
                    failed.add(td)
                }
            }
        }
        if (failed.isNotEmpty()) {
            val msg = failed.joinToString(prefix = "Failed:\n", separator = "\n", postfix = "\n") { "${it} Regex(${it.globPattern.toRegexFromGlob(it.separator)})" }
            fail(msg)
        }
    }


}