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
