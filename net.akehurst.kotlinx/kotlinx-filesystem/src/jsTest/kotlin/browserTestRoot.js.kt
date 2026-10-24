/**
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

package net.akehurst.kotlinx.filesystem

import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import web.experimental.ExperimentalWebApi
import web.navigator.navigator
import web.storage.getDirectory

@OptIn(ExperimentalWebApi::class)
actual suspend fun browserTestRoot(): DirectoryHandle? {
    // the js target also runs its tests on Node.js, which has no browser file system
    val inBrowser = js("typeof window !== 'undefined'") as Boolean
    if (inBrowser.not()) return null
    return DirectoryHandleJS(UserFileSystemDefault, null, navigator.storage.getDirectory())
}
