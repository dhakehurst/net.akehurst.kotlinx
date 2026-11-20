# kotlinx-filesystem-compose-demo

A small Compose Desktop app that opens the folder chooser of
`UserFileSystemDefault.selectDirectoryFromDialog` from a Compose coroutine
(`rememberCoroutineScope().launch` in a button's `onClick`), and shows the chosen folder.

It is a separate Gradle build, and uses `kotlinx-filesystem` from the sources in this repository
(`includeBuild("../net.akehurst.kotlinx")`). That build needs the `exportPublic` plugin snapshot in
`mavenLocal`, as for any build of `net.akehurst.kotlinx`.

```
./gradlew run                  # press "Choose folder", pick a folder, press Open or Cancel
./gradlew run --args=--auto    # clicks the button, approves a temporary folder after 2 s, exits 0 if the
                               # chooser returned it, 1 on failure or after 30 s
```

`--auto` needs a display (on Linux, `Xvfb` is enough) and does not work on macOS, where the
native `FileDialog` is used and cannot be closed from code. On Linux the chooser uses the GTK look
and feel when the desktop is GNOME, for example with `XDG_CURRENT_DESKTOP=GNOME`.

The demo keeps Swing's default look and feel for the app
(`compose.application.configure.swing.globals=false`), as when an app is started with plain `java`
or from an IDE. Before the fix in `kotlinx-filesystem`, opening the chooser that way switched the
whole app to the system look and feel and ran the dialog inside Compose's coroutine dispatcher,
which ended the app with "Fatal exception in coroutines machinery".
