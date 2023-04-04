# capsule
Capsule is a minimal dependency injection library. It is _not_ a framework, and it does not care in
which context it is used or for what purpose. Its only job is to make it is easier for you to
resolve dependencies in your application.

## Usage
Capsule allows just to resolve dependencies for application, by configuring once how to set up and
instantiate a class, and then this class can be fetched anywhere in you application where you can
access this capsule.

```kotlin
val capsule = Capsule {
    register {
        Foo(Bar())
    }
}

class Foo(val bar: Bar)
class Bar

fun main() {
    // Get instance of Foo
    val foo: Foo = capsule.get()
}
```

If your dependency resolution is trivial, and it is not resolved as an implementation
of an interface or abstract class, it may be possible to resolve it automatically without any
configuration or setup.

```kotlin
class Foo(val bar: Bar)
class Bar

val capsule = Capsule()

fun main() {
    // This works too, since there is only one way to instantiate class Foo and Bar
    val foo: Foo = capsule.get()
}
```

In more complex cases, such as when you depend on an interface, and you must specify which
dependency to use for that interface, some manual setup is needed.

```kotlin
class Service(val client: Client)

interface Client {
    fun send()
}

class ClientImpl(val config: Config): Client {
    override fun send() {
        // Do something
    }
}

data class Config(val username: String, val password: String)

val capsule = Capsule {
    // Here we must register an implementation of Client, otherwise we wouldn't know
    // which implementation of Client to use when requesting a Client.
    register {
        // Calling get() will return an instantiated Config 
        ClientImpl(get())
    }
    
    // Note that it is perfectly fine to register "Config" after "ClientImpl", even though the first
    // depends on the second. This is since all dependency instantiation is lazy, and nothing inside
    // the register block is executed until a dependency is actually needed.
    register {
        Config("username", "secret-password")
    }
}

fun main() {
    val client: Client = capsule.get()
}
```

By specifying an explicit type annotation and instead of an inferred type, when registering a class,
we can control for the scope for which a dependency is registered.

So consider
```kotlin
register { ClientImpl(get()) }
```
versus
```kotlin
register(ClientImpl::class.java) { ClientImpl(get()) }
```

In the first case, we can get an instance of ClientImpl by both calling
- `val client: ClientImpl = capsule.get()`
- `val client: Client = capsule.get()`

but when we use `register(ClientImpl::class.java)` we are saying that only
`val client: ClientImpl = capsule.get()` should be permitted.

This may be useful if you have a class that implements many interfaces, and for some reason you only
want capsule to consider it for one or some of those interfaces.

It is also possible to combine several capsules. For example, you might have a capsule with "common"
dependencies which will always be used regardless of context, and then there might be a test capsule
that contains mocks or dependencies which you only wants to use when running tests.

This can be setup like this
```kotlin
val common = Capsule {
    // Register dependencies that will be used in all contexts
}

val primary = Capsule(common) {
    // Register dependencies that will only be used in live environment
    // These dependencies can in turn depend on dependencies which were set up in the common capsule 
}

val test = Capsule(common) {
    // Register dependencies which you may only want to use in tests. This should preferably
    // be set up in such way that it cannot be accessible from your regular code, only by your tests
}
```

Dependencies in a child module will always have higher priority than dependencies in a parent module.

```kotlin
import java.time.Clock
import java.time.Instant

val parent = Capsule {
    register<Clock> {
        Clock.systemUTC()
    }
}

val child = Capsule(parent) {
    // This Clock will have priority over the Clock registered in the parent
    register<Clock> {
        Clock.fixed(Instant.EPOCH)
    }
}

// This will always return the Clock that returns an `Instant.EPOCH`
val clock: Clock = child.get()
```

This means that it is possible to register all your "real" dependencies in one module and then only override the ones
that needs to be different in tests with other implementations.

Sometimes you need all implementations of an interfaces. For such cases you can use `getMany`.

```kotlin
interface Greeter
class Foo : Greeter
class Bar : Greeter

val capsule = Capsule {
    register { Foo() }
    register { Bar() }
}

fun main() {
    val greeters: List<Greeter> = capsule.getMany()
}
```

## Gradle Dependency
Add this to your build.gradle(.kts)
```kotlin
repositories {
    // Add this as a repository
    github("venture-falcon/capsule")
}

// This is needed to be able to access artifacts published on GitHub
// See also https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry#using-a-published-package
fun RepositoryHandler.github(vararg repos: String) {
    repos.forEach { repo ->
        maven {
            name = "GithubPackages"
            url = uri("https://maven.pkg.github.com/$repo")
            credentials {
                username = requireNotNull(System.getenv("GPR_USER")) {
                    "Found no username for GitHub packages access"
                }
                password = requireNotNull(System.getenv("GPR_TOKEN")) {
                    "Found no token for GitHub packages access"
                }
            }
        }
    }
}

dependencies {
    // Then add this to you dependencies block, and make sure to set a proper version number
    implementation("io.nexure:capsule:VERSION_NUMBER")
}
```
