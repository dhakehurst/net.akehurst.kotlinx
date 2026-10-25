/*
 * Copyright (C) 2024 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

package net.akehurst.kotlinx.text

//TODO: hopefully this will be in the kotlin-stdlib at some point
/*
 * Modified from code given in [https://stackoverflow.com/questions/1247772/is-there-an-equivalent-of-java-util-regex-for-glob-type-patterns]
 * ("I hereby grant that the code in this answer is in the public domain. – Neil Traft Apr 17 at 18:49")
 *
 * Converts a standard POSIX Shell globbing pattern into a regular expression
 * pattern. The result can be used with the standard {@link java.util.regex} API to
 * recognize strings which match the glob pattern.
 * <p/>
 * See also, the POSIX Shell language:
 * http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html#tag_02_13_01
 *
 * original author Neil Traft
 *
 */
/**
 * Converts the glob string into a regular expression [Regex] with the specified set of [options].
 *
 * @param separator the character that separates the glob parts,
 * i.e. '.' (for kotlin qualified names) or '/' (for file paths)
 *
 */
fun String.toRegexFromGlob(separator: Char, options: Set<RegexOption> = emptySet()): Regex {
    val globStr = this
    val sep = separator.escapedForCharacterClass()
    var regex = ""
    var inGroup = 0
    var inCharacterClass = 0
    var firstIndexInClass = -1

    var i = 0
    while (i < globStr.length) {
        val ch = globStr[i]
        when (ch) {
            '\\' -> {
                if (++i >= globStr.length) {
                    regex += '\\'
                } else {
                    val next = globStr[i]
                    when (next) {
                        ',' -> Unit /* escape not needed */
                        'Q', 'E' ->  regex += "\\\\" /* extra escape needed */
                        else -> regex += '\\'
                    }
                    regex += next
                }
            }
            '*' -> {
                if (inCharacterClass == 0) {
                    // Standard globstar: '**' as a whole segment matches zero or more segments;
                    // anywhere else it is the same as '*'.
                    val isDouble = i + 1 < globStr.length && globStr[i + 1] == '*'
                    val startsSegment = i == 0 || globStr[i - 1] == separator
                    val afterDouble = i + 2
                    when {
                        isDouble && startsSegment && afterDouble >= globStr.length -> {
                            regex += ".*"
                            i++
                        }
                        isDouble && startsSegment && globStr[afterDouble] == separator -> {
                            regex += "(?:.*[$sep])?"
                            i += 2
                        }
                        isDouble -> {
                            regex += "[^$sep]*"
                            i++
                        }
                        else -> regex += "[^$sep]*"
                    }
                } else {
                    regex += '*'
                }
            }
            '?' -> regex += if (inCharacterClass == 0) "([^$sep])" else '?'
            '[' -> {
                inCharacterClass++
                firstIndexInClass = i + 1
                // A class must never match the separator. The lookahead does this on every platform
                // (JS regexes have no class intersection '&&').
                regex += "(?![$sep])["
            }
            ']' -> {
                if (inCharacterClass == 0) throw IllegalArgumentException("Unmatched ']' at index $i in glob '$globStr'")
                inCharacterClass--
                regex += ']'
            }
            '{' -> {
                inGroup++
                regex += '('
            }
            '}' -> {
                inGroup--
                regex += ')'
            }
            '!' -> regex += if (firstIndexInClass == i) '^' else '!'
            ',' -> regex += if (inGroup > 0) '|' else ','
            '.', '(', ')', '+', '|', '^', '$' -> {
                if (inCharacterClass == 0 || (firstIndexInClass == i && ch == '^')) regex += '\\'
                regex += ch
            }
            else -> regex += ch
        }
        i++
    }

    return Regex(regex, options)
}

/**
 * Escapes this character for use inside a regex character class, in a form valid on both JVM and JS (unicode mode).
 */
private fun Char.escapedForCharacterClass(): String = if (this in "\\]^-[") "\\$this" else this.toString()
