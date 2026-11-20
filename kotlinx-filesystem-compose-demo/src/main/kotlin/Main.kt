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

package net.akehurst.kotlinx.filesystem.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.akehurst.kotlinx.filesystem.UserFileSystemDefault
import net.akehurst.kotlinx.filesystem.api.FileAccessMode
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.Toolkit
import java.awt.event.WindowEvent
import java.nio.file.Files
import javax.swing.JFileChooser
import javax.swing.Timer
import javax.swing.UIManager
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Opens the folder chooser of UserFileSystemDefault.selectDirectoryFromDialog from a Compose coroutine
 * (rememberCoroutineScope().launch, as in a button's onClick), and shows the result.
 *
 *   ./gradlew run                 - press "Choose folder", pick a folder, press Open or Cancel; the app keeps running
 *   ./gradlew run --args=--auto   - clicks the button (java.awt.Robot), approves a temporary folder from code, checks the result,
 *                                   and exits with 0 on success or 1 on failure/timeout (not on macOS, where the
 *                                   native FileDialog is used and cannot be closed from code)
 *
 * On Linux the chooser uses the system (GTK) look-and-feel only when the desktop is GNOME, e.g. XDG_CURRENT_DESKTOP=GNOME.
 */
@Volatile
private var chooserOpened = false

fun main(args: Array<String>) {
    // Keep Swing's default look-and-feel, as in an app started with plain `java` (or from an IDE) rather than
    // Compose's `run` task or packaged launcher, which set the system look-and-feel at startup. The chooser must
    // work in both cases; this is the one where it used to switch the app's look-and-feel and crash.
    System.setProperty("compose.application.configure.swing.globals", "false")
    val auto = args.contains("--auto")
    val autoDir = if (auto) Files.createTempDirectory("compose-demo-").toFile() else null
    if (auto) {
        approveChooserWhenOpened(autoDir!!)
        thread(isDaemon = true) {
            Thread.sleep(30_000)
            println("DEMO: FAILED - no result after 30s")
            exitProcess(1)
        }
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "kotlinx-filesystem dialog demo") {
            val scope = rememberCoroutineScope()
            var result by remember { mutableStateOf("(none)") }
            var count by remember { mutableStateOf(0) }
            val choose: () -> Unit = {
                scope.launch {
                    chooserOpened = true
                    println("DEMO: opening chooser on ${Thread.currentThread().name}, look-and-feel ${UIManager.getLookAndFeel().name}")
                    val dir = UserFileSystemDefault.selectDirectoryFromDialog("Choose folder", null, FileAccessMode.READ_ONLY)
                    count++
                    result = dir?.absolutePath ?: "(cancelled)"
                    println("DEMO: result ${result}, look-and-feel ${UIManager.getLookAndFeel().name}")
                    if (auto) {
                        val ok = dir?.absolutePath == autoDir!!.absolutePath
                        println(if (ok) "DEMO: OK" else "DEMO: FAILED - expected ${autoDir.absolutePath}")
                        autoDir.delete()
                        exitProcess(if (ok) 0 else 1)
                    }
                }
            }
            var buttonCentre by remember { mutableStateOf<Offset?>(null) }
            if (auto) {
                // press the button with a real mouse click, so the chooser is opened from the click handler
                LaunchedEffect(buttonCentre) {
                    val c = buttonCentre ?: return@LaunchedEffect
                    // a click can be lost while the window is still being mapped, so click until the chooser opens
                    while (!chooserOpened) {
                        delay(1000)
                        val onScreen = window.contentPane.locationOnScreen
                        val scale = window.graphicsConfiguration.defaultTransform.scaleX
                        val x = onScreen.x + (c.x / scale).toInt()
                        val y = onScreen.y + (c.y / scale).toInt()
                        thread {
                            val robot = Robot()
                            robot.mouseMove(x, y)
                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                        }
                        delay(2000)
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = choose,
                    modifier = Modifier.onGloballyPositioned { buttonCentre = it.boundsInWindow().center }
                ) { Text("Choose folder") }
                Text("Result $count: $result")
            }
        }
    }
}

private fun approveChooserWhenOpened(dir: java.io.File) {
    Toolkit.getDefaultToolkit().addAWTEventListener({ ev ->
        if (ev.id == WindowEvent.WINDOW_OPENED) {
            findChooser((ev as WindowEvent).window)?.let { chooser ->
                // leave the dialog open for a while, as a person would, so Compose renders frames while it is open
                Timer(2000) {
                    chooser.selectedFile = dir
                    chooser.approveSelection()
                }.apply { isRepeats = false }.start()
            }
        }
    }, AWTEvent.WINDOW_EVENT_MASK)
}

private fun findChooser(c: Component): JFileChooser? = when (c) {
    is JFileChooser -> c
    is Container -> c.components.firstNotNullOfOrNull { findChooser(it) }
    else -> null
}
