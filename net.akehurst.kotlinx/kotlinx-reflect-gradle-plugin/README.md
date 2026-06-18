# Akehurst Kotlinx Reflection Framework

This plugin helps overcome the koltin limitations around reflection outside of a JVM Platform.

Currently supported platforms:
 - JVM
 - JS
 - (WasmJS - In work)

The basic principles are as follows:
 - Define via this plugin which packages/classes you want to be available via reflection
 - Extra code is generated at compile time that makes reflection information available
 - Invoke reflection calls via the KotlinxReflect API

**Currently**, the module that provides the classes must define what will be used by reflection.

The intention is that a module that uses classes by reflection should be able to define the usage,
this worked in older version of kotlin, but not any more.


# Example Usage

## Provider

Configure, in the providing module build.gradle.kts file which classes to generate reflection information for. 

```
plugins {
    kotlin("multiplatform") version ("<version>")
    id("net.akehurst.kotlin.gradle.plugin.exportPublic") version("<version>")
    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") version ("<version>")
}

kotlin {
    js {
        binaries.library()
    }
}

kotlinxReflect {
    forReflectionMain.set(
        listOf(
            "mymodule.*"
        )
    )
}
```

with code:

```
package mymodule

class AAAA {
    var prop1 : String = "hello"
}
```


## Consumer (Usage)

Configure, in a consuming module build.gradle.kts a dependency to kotlinx-reflect API.

```
plugins {
    kotlin("multiplatform") version ("<version>")
}

dependencies {
    commonMainImplementation("net.akehurst.kotlinx:kotlinx-reflect:"<version>"")
    commonMainImplementation(project(":kotlinx-reflect-gradle-plugin-test-moduleForReflection"))
}

kotlin {
    js { }
}
```

And then in code:

```

KotlinxReflectForModule.registerUsedClasses()

// to construct a class by reflection
val cls = KotlinxReflect.classForName("mymodule.AAAA")
val obj = cls.reflect().construct()

// and get the value of a property
val result = obj.reflect().getProperty("prop1")

```