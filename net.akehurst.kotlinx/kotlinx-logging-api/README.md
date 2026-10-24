# kotlinx-logging

Simple logging for Kotlin Multiplatform. Code logs through one small API on every platform (JVM, JS,
Wasm and Native), and the app chooses where the output goes: the console, the browser console, or
log4j.

```kotlin
private val log = logger("com.example.Server")

log.logInformation { "started on port $port" }
log.logError(ex) { "request failed" }
```

## Contents

- [Modules](#modules)
- [Setup](#setup)
- [Logging](#logging)
- [Levels and thresholds](#levels-and-thresholds)
- [Choosing a logging framework](#choosing-a-logging-framework)
- [Log4j (JVM)](#log4j-jvm)
- [Browser console (JS and Wasm)](#browser-console-js-and-wasm)
- [Bridges: output of other logging libraries](#bridges-output-of-other-logging-libraries)
- [Writing your own framework](#writing-your-own-framework)
- [API](#api)
- [Migrating from earlier versions](#migrating-from-earlier-versions)

## Modules

| Module | What it contains | Use it in |
|---|---|---|
| `kotlinx-logging-api` | The API (`Logger`, `LogLevel`, `LoggingManager`, ...) and a console logger for all platforms | Libraries |
| `kotlinx-logging-common` | Frameworks: log4j on the JVM, the browser console on JS and Wasm | Apps |
| `kotlinx-logging-with-bridges` | Everything in `kotlinx-logging-common`, plus routing of other logging libraries' output | Apps |

A library only needs `kotlinx-logging-api`. The app that uses the library picks the output by adding
`kotlinx-logging-common` or `kotlinx-logging-with-bridges`, and calling
[`LoggingManager.use`](#choosing-a-logging-framework).

## Setup

The modules are published to Maven Central under the group `net.akehurst.kotlinx`.

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // in a library
                implementation("net.akehurst.kotlinx:kotlinx-logging-api:<version>")

                // or, in an app
                implementation("net.akehurst.kotlinx:kotlinx-logging-with-bridges:<version>")
            }
        }
    }
}
```

## Logging

Get a logger by name, usually one per class or file:

```kotlin
import net.akehurst.kotlinx.logging.api.logger

private val log = logger("com.example.Server")
```

Then log with one of the level functions. The message is a lambda, so it is only built when the
message will actually be output:

```kotlin
log.logFatal { "cannot continue" }
log.logError(ex) { "request failed: ${request.id}" }   // an optional Throwable comes first
log.logWarning { "retrying" }
log.logInformation { "started on port $port" }
log.logDebug { "config: $config" }
log.logTrace { "entering handle()" }

log.log(LogLevel.Debug) { "same as logDebug" }
```

Each function also has an `...If` form that only logs when a condition holds:

```kotlin
log.logDebugIf({ request.isSlow }) { "slow request: ${request.id}" }
```

## Levels and thresholds

`LogLevel` is the level of a message, from most to least severe:

| `LogLevel` | log4j | Browser console |
|---|---|---|
| `Fatal` | `FATAL` | `console.error` |
| `Error` | `ERROR` | `console.error` |
| `Warning` | `WARN` | `console.warn` |
| `Information` | `INFO` | `console.info` |
| `Debug` | `DEBUG` | `console.debug` |
| `Trace` | `TRACE` | `console.debug` |

`LogThreshold` is the root level, which decides which messages are output. It is either a `LogLevel`
(output messages at that level or more severe), `LogThreshold.None` (output nothing) or
`LogThreshold.All` (output everything).

```kotlin
LoggingManager.rootLoggingLevel = LogLevel.Debug          // Fatal .. Debug are output
LoggingManager.rootLoggingLevel = LogThreshold.None       // nothing is output
LoggingManager.rootLoggingLevel = LogThreshold.All        // everything is output

LogLevel.Information.allows(LogLevel.Debug)               // false
```

Changing the root level applies straight away, including to loggers that already exist.

## Choosing a logging framework

`LoggingManager` holds the logging framework in use. Until the app chooses one, it is
`LoggingByConsole`, which prints `Level: name - message` lines (and the stack trace of a `Throwable`)
using `println`, at threshold `Information`.

Choose a framework once, at startup:

```kotlin
// JVM
LoggingManager.use(LoggingByLog4J)

// JS or Wasm
LoggingManager.use(LoggingByBrowserConsole)

// with a root level
LoggingManager.use(LoggingByLog4J, LogLevel.Debug)
```

When no level is given, the framework keeps its own level. For log4j that is the level from its
configuration file. When the framework is set or changed, `LoggingManager` logs `using <framework>`
at `Debug` level, so the framework in use can be checked by turning on debug output.

| Framework | Module | Platforms |
|---|---|---|
| `LoggingByConsole` | `kotlinx-logging-api` | All |
| `LoggingByLog4J` | `kotlinx-logging-common` | JVM |
| `LoggingByBrowserConsole` | `kotlinx-logging-common` | JS, Wasm |

## Log4j (JVM)

`LoggingByLog4J` sends each message to the log4j logger of the same name. So log4j's usual
configuration applies: a `log4j2.xml` (or `log4j2.yaml` with `kotlinx-logging-with-bridges`) on the
classpath configures appenders, layouts and per-logger levels.

- **Root level**: `LoggingManager.rootLoggingLevel` reads and sets log4j's root level. A custom log4j
  level reads back as the most verbose `LogLevel` it still lets through. For example, a custom level
  between `WARN` and `INFO` reads back as `LogLevel.Warning`.
- **Caller location**: log4j reports the code that called the logger, not the logger itself, so
  layout patterns such as `%C`, `%M` and `%L` work as expected.
- **Dependencies**: `kotlinx-logging-common` exposes `log4j-api` to your code, and puts `log4j-core`
  on the runtime classpath. Code that uses `org.apache.logging.log4j.core` classes directly needs its
  own `log4j-core` dependency.

## Browser console (JS and Wasm)

`LoggingByBrowserConsole` logs to the JavaScript `console` (the browser's developer tools, or the
terminal under Node.js), using the console function that matches the level (see the
[table above](#levels-and-thresholds)). Each line is `Level: name - message`.

- **Exceptions** are passed to the same console call as the message. On JS the exception itself is
  passed, so the browser shows its stack trace and can link to the source. On Wasm its stack trace is
  passed as text.
- **Timing**: when a logger's name starts with `Send` or `Receiv`, each line shows the time in
  milliseconds since the previous such line from that logger, e.g. `Info (12.3ms): ...`.

## Bridges: output of other logging libraries

Libraries in an app may use other logging libraries. `kotlinx-logging-with-bridges` routes their
output into `LoggingManager`, so it all goes to the same place.

### JVM: SLF4J, Commons Logging, JUL and log4j 1.x

On the JVM these are routed to log4j, for use with `LoggingByLog4J`. Only the dependency is needed:

| Library | Routed by |
|---|---|
| SLF4J 2 | `log4j-slf4j2-impl` |
| Apache Commons Logging | `log4j-jcl` |
| `java.util.logging` (JUL) | `log4j-jul` |
| log4j 1.x | `log4j-1.2-api` |

For JUL, `LoggingByLog4J` installs log4j's bridge handler itself, so the JVM does not need to be
started with `-Djava.util.logging.manager`. JUL's own console handler is removed so messages are not
output twice, and JUL loggers follow log4j's levels. If the system property is already set, it is
left to do the routing.

### Kermit and kotlin-logging

[Kermit](https://github.com/touchlab/Kermit) and
[kotlin-logging](https://github.com/oshai/kotlin-logging) are routed to `LoggingManager` once the app
calls `LoggingBridges.install()`, typically next to `LoggingManager.use(...)`:

```kotlin
LoggingManager.use(LoggingByLog4J)
LoggingBridges.install()
```

| Library | Platforms | Logger name | Levels |
|---|---|---|---|
| Kermit | All | The Kermit tag, or `Kermit` when the tag is empty | `Verbose` → `Trace`, `Debug`, `Info` → `Information`, `Warn` → `Warning`, `Error`, `Assert` → `Fatal` |
| kotlin-logging | JS, Wasm, Native | The kotlin-logging logger name | `TRACE`, `DEBUG`, `INFO` → `Information`, `WARN` → `Warning`, `ERROR` |

On the JVM, kotlin-logging is left on its own route through SLF4J, which `log4j-slf4j2-impl` sends to
log4j.

Loggers of both libraries that were created before `install()` are also routed. For kotlin-logging,
such loggers build every message, and `LoggingManager` then drops the ones below the root level;
loggers created after `install()` check the level first.

## Writing your own framework

A framework implements `LoggingFramework`: it holds the root level and creates loggers.

```kotlin
object LoggingToMyService : LoggingFramework {
    override var rootLoggingLevel: LogThreshold = LogLevel.Information

    override fun logger(prefix: String): Logger = object : Logger {
        override fun log(level: LogLevel, t: Throwable?, lazyMessage: () -> String) {
            if (rootLoggingLevel.allows(level)) {
                MyService.send(prefix, level.name, lazyMessage(), t)
            }
        }
    }
}

LoggingManager.use(LoggingToMyService)
```

Only `Logger.log` has to be implemented; the level functions such as `logInformation` call it.

## API

All of these are in the package `net.akehurst.kotlinx.logging.api`, except where noted.

| Declaration | Description |
|---|---|
| `fun logger(prefix: String): Logger` | A logger from the framework in use |
| `interface Logger` | `log(level, t, lazyMessage)`, the level functions `logFatal` .. `logTrace`, and their `...If` forms |
| `enum class LogLevel` | Message levels `Fatal`, `Error`, `Warning`, `Information`, `Debug`, `Trace` |
| `sealed interface LogThreshold` | Root levels: any `LogLevel`, `None` or `All`; `allows(level)` |
| `object LoggingManager` | `use(framework, rootLoggingLevel?)`, `rootLoggingLevel`, `logger(prefix)` |
| `interface LoggingFramework` | `rootLoggingLevel` and `logger(prefix)`, for [your own framework](#writing-your-own-framework) |
| `object LoggingByConsole` | The default framework, using `println` |
| `object LoggingByLog4J` | log4j framework (JVM), in `net.akehurst.kotlinx.logging.common` |
| `LoggerLog4J.addWrapperClassNames(...)` | Lets a bridge make log4j report its caller as the location, in `net.akehurst.kotlinx.logging.common` |
| `object LoggingByBrowserConsole` | Browser console framework (JS and Wasm), in `net.akehurst.kotlinx.logging.common` |
| `object LoggingBridges` | `install()` for Kermit and kotlin-logging, in `net.akehurst.kotlinx.logging.bridges` |

## Migrating from earlier versions

| Before | Now |
|---|---|
| `LogLevel.None`, `LogLevel.All` | `LogThreshold.None`, `LogThreshold.All` |
| `rootLoggingLevel` is a `LogLevel` | `rootLoggingLevel` is a `LogThreshold`; a `LogLevel` can still be assigned |
| Levels compared with `<`, `>` | `threshold.allows(level)` |
| `net.akehurst.kotlinx.logging.common.LoggingByConsole` | `net.akehurst.kotlinx.logging.api.LoggingByConsole` |
| `LoggingByBrowserConsoleJs`, `LoggingByBrowserConsoleWasm` | `LoggingByBrowserConsole` |
| `use(framework)` sets the root level to `Information` | `use(framework)` keeps the framework's own level |
| `using <framework>` logged at `Information` | Logged at `Debug` |
| `log4j-core` on the compile classpath | Only `log4j-api`; add `log4j-core` to use its classes directly |
| JUL needed `-Djava.util.logging.manager` | Routed automatically when `log4j-jul` is present |
