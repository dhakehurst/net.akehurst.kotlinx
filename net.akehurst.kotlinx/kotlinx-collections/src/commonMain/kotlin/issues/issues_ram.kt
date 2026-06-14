/**
 * Copyright (C) 2023 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlinx.issues.ram

import net.akehurst.kotlinx.issues.api.*

data class IssueDefault<L:Any>(
    override val kind:IssueKind,
    override val location: L?,
    override val message: String,
    override val data: Any? = null
) : Issue<L>

operator fun <L:Any> IssueCollection<L>.plus(other: IssueCollection<L>): IssueHolder<L> {
    val issues = IssueHolder<L>()
    issues.addAllFrom(this)
    issues.addAllFrom(other)
    return issues
}

class IssueHolder<L:Any>(
    issues: Iterable<Issue<L>> = emptyList()
) : IssueCollection<L> {

    private val _issues = issues.toMutableSet()

    override val all: Set<Issue<L>> get() = _issues
    override val errors: List<Issue<L>> get() = _issues.filter { it.kind == IssueKind.ERROR }
    override val warnings: List<Issue<L>> get() = _issues.filter { it.kind == IssueKind.WARNING }
    override val informations: List<Issue<L>> get() = _issues.filter { it.kind ==IssueKind.INFORMATION }

    fun clear() {
        _issues.clear()
    }

    fun raise(kind: IssueKind, location: L?, message: String, data: Any? = null) {
        _issues.add(IssueDefault(kind, location, message, data))
    }

    fun info(location: L?, message: String, data: Any? = null) =
        raise(IssueKind.INFORMATION,  location, message, data)

    fun warn(location: L?, message: String, data: Any? = null) =
        raise(IssueKind.WARNING,  location, message, data)


    fun error(location: L?, message: String, data: Any? = null) =
        raise(IssueKind.ERROR,  location, message, data)

    fun addAll(issues: Iterable<Issue<L>>) {
        this._issues.addAll(issues)
    }

    fun addAllFrom(other: IssueCollection<L>) {
        this.addAll(other.all)
    }

    override val size: Int get() = this._issues.size
    override fun iterator(): Iterator<Issue<L>> = this._issues.iterator()
    override fun isEmpty(): Boolean = this._issues.isEmpty()
    override fun contains(element: Issue<L>): Boolean = this._issues.contains(element)
    override fun containsAll(elements: Collection<Issue<L>>): Boolean = this._issues.containsAll(elements)

    override fun toString(): String = this._issues.joinToString(separator = "\n") { "$it" }

}