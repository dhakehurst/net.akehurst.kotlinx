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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileAccessMode
import java.awt.AWTEvent
import java.awt.Container
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

/**
 * Opens and closes the JVM directory/file chooser from a coroutine that runs on the AWT event dispatch thread,
 * as a Compose Desktop click handler does with rememberCoroutineScope().launch { ... }.
 *
 * Needs a display (on Linux, Xvfb is enough); skipped when headless, and on macOS where the native FileDialog
 * is used and cannot be closed from the test.
 */
class test_UserFileSystemDefault_dialogs {

    /**
     * Like Compose Desktop's FlushCoroutineDispatcher: runs coroutine tasks on the event dispatch thread,
     * and records whether the current code is running inside one of its tasks.
     */
    private class EdtTaskDispatcher : CoroutineDispatcher() {
        @Volatile
        var insideTask = false
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            EventQueue.invokeLater {
                insideTask = true
                try {
                    block.run()
                } finally {
                    insideTask = false
                }
            }
        }
    }

    private lateinit var tmp: File

    @BeforeTest
    fun setUp() {
        tmp = Files.createTempDirectory("kotlinx-filesystem-dialog-test").toFile()
        File(tmp, "sub").mkdir()
        File(tmp, "a.txt").writeText("hello")
    }

    @AfterTest
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private val canShowSwingChooser: Boolean
        get() = !GraphicsEnvironment.isHeadless() &&
                System.getProperty("os.name")?.lowercase()?.startsWith("mac") != true

    /**
     * Calls [action] with the dialog that is shown while [block] runs, and records whether the dialog was opened
     * from inside a task of [dispatcher] (which is what crashed Compose Desktop).
     */
    private fun <T> withDialogCloser(dispatcher: EdtTaskDispatcher, action: (JFileChooser) -> Unit, block: suspend () -> T): Pair<T, Boolean> {
        var openedInsideTask: Boolean? = null
        val listener = AWTEventListener { ev ->
            if (ev.id == WindowEvent.WINDOW_OPENED) {
                val chooser = findChooser((ev as WindowEvent).window)
                if (null != chooser) {
                    openedInsideTask = dispatcher.insideTask
                    EventQueue.invokeLater { action(chooser) }
                }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
        try {
            val result = runBlocking {
                withTimeout(30_000) {
                    async(dispatcher) { block() }.await()
                }
            }
            assertNotNull(openedInsideTask, "the chooser was not shown")
            return Pair(result, openedInsideTask!!)
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            Window.getWindows().forEach { if (it.isDisplayable && null != findChooser(it)) it.dispose() }
        }
    }

    private fun findChooser(c: java.awt.Component): JFileChooser? = when (c) {
        is JFileChooser -> c
        is Container -> c.components.firstNotNullOfOrNull { findChooser(it) }
        else -> null
    }

    @Test
    fun selectDirectoryFromDialog_from_edt_coroutine_approve() {
        if (!canShowSwingChooser) return
        val dispatcher = EdtTaskDispatcher()
        val lafBefore = UIManager.getLookAndFeel()?.javaClass?.name
        val sub = File(tmp, "sub")
        val (dir, openedInsideTask) = withDialogCloser(dispatcher, { chooser ->
            chooser.selectedFile = sub
            chooser.approveSelection()
        }) {
            assertTrue(EventQueue.isDispatchThread())
            UserFileSystemDefault.selectDirectoryFromDialog("test", DirectoryHandleJVM(UserFileSystemDefault, null, tmp), FileAccessMode.READ_ONLY)
        }
        assertFalse(openedInsideTask, "the modal dialog must not run inside the caller's coroutine task")
        assertNotNull(dir)
        assertEquals(sub.absolutePath, (dir as DirectoryHandle).absolutePath)
        assertEquals(lafBefore, UIManager.getLookAndFeel()?.javaClass?.name, "the app's look-and-feel must be restored")
    }

    @Test
    fun selectDirectoryFromDialog_from_edt_coroutine_cancel() {
        if (!canShowSwingChooser) return
        val dispatcher = EdtTaskDispatcher()
        val (dir, openedInsideTask) = withDialogCloser(dispatcher, { it.cancelSelection() }) {
            UserFileSystemDefault.selectDirectoryFromDialog("test", null, FileAccessMode.READ_ONLY)
        }
        assertFalse(openedInsideTask)
        assertNull(dir)
    }

    @Test
    fun selectExistingFileFromDialog_from_edt_coroutine_approve() {
        if (!canShowSwingChooser) return
        val dispatcher = EdtTaskDispatcher()
        val a = File(tmp, "a.txt")
        val (file, openedInsideTask) = withDialogCloser(dispatcher, { chooser ->
            chooser.selectedFile = a
            chooser.approveSelection()
        }) {
            UserFileSystemDefault.selectExistingFileFromDialog("test", DirectoryHandleJVM(UserFileSystemDefault, null, tmp), FileAccessMode.READ_ONLY, useNativeDialog = false)
        }
        assertFalse(openedInsideTask)
        assertEquals(a.absolutePath, file?.let { (it as FileHandleJVM).handle.absolutePath })
    }

    @Test
    fun selectDirectoryFromDialog_from_background_thread() {
        if (!canShowSwingChooser) return
        val sub = File(tmp, "sub")
        var opened = false
        val listener = AWTEventListener { ev ->
            if (ev.id == WindowEvent.WINDOW_OPENED) {
                findChooser((ev as WindowEvent).window)?.let { chooser ->
                    opened = true
                    EventQueue.invokeLater {
                        chooser.selectedFile = sub
                        chooser.approveSelection()
                    }
                }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
        try {
            val dir = runBlocking {
                withTimeout(30_000) {
                    UserFileSystemDefault.selectDirectoryFromDialog("test", DirectoryHandleJVM(UserFileSystemDefault, null, tmp), FileAccessMode.READ_ONLY)
                }
            }
            assertTrue(opened)
            assertEquals(sub.absolutePath, dir?.absolutePath)
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
    }
}
