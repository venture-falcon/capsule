package io.nexure.capsule

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapsuleTest {
    @Test
    fun `test explicit dependency can be registered and found`() {
        class Bar() {
            fun hello(): String = "Hello, world!"
        }

        class Foo(val bar: Bar) {
            fun hello(): String = bar.hello()
        }

        val capsule = Capsule {
            register {
                // Here we explicitly set Bar, all thought it should not be needed
                Foo(bar = Bar())
            }
        }

        val foo: Foo = capsule.get()
        assertEquals("Hello, world!", foo.hello())
    }

    @Test
    fun `test implicit dependency can be found`() {
        class Bar() {
            fun hello(): String = "Hello, world!"
        }

        class Foo(val bar: Bar) {
            fun hello(): String = bar.hello()
        }

        val capsule = Capsule()
        val foo: Foo = capsule.get()
        assertEquals("Hello, world!", foo.hello())
    }

    @Test
    fun `test register implementation for interface and get`() {
        class GreeterImplementation(): Greeter {
            override fun hello(): String = "Hello, world!"
        }

        val capsule = Capsule {
            // Here we must register GreeterImplementation for Greeter, otherwise we wouldn't know
            // which implementation to use when requesting a Greeter.
            register {
                GreeterImplementation()
            }
        }
        val greeter: Greeter = capsule.get()
        assertEquals("Hello, world!", greeter.hello())
    }

    @Test
    fun `test register class only for implementation class and get`() {
        class GreeterImplementation(): Greeter {
            override fun hello(): String = "Hello, world!"
        }

        val capsule = Capsule {
            // By specifying an explicit class, it will only be registered under that class.
            // So even if GreeterImplementation implements interface Greeter, it will not be
            // returned when just requesting a Greeter.
            register(GreeterImplementation::class.java) {
                GreeterImplementation()
            }
        }
        val greeter: Greeter? = capsule.tryGet()
        assertNull(greeter)
        val greeterImpl: GreeterImplementation = capsule.get()
        assertEquals("Hello, world!", greeterImpl.hello())
    }

    @Test
    fun `test with multiple combined capsules`() {
        class Serializer(val version: Int)
        class Service(val greeter: Greeter, val serializer: Serializer) {
            fun greet(): String = greeter.hello()
        }

        class LiveGreeter : Greeter {
            override fun hello(): String = "This is live"
        }

        class TestGreeter : Greeter {
            override fun hello(): String = "This is test"
        }

        val common = Capsule {
            // This dependency is used in both live code and test
            register { Serializer(1) }
        }

        val main = Capsule(common) {
            register { LiveGreeter() }
            register { Service(get(), get()) }
        }

        val test = Capsule(common) {
            register { TestGreeter() }
            register { Service(get(), get()) }
        }

        assertEquals("This is live", main.get<Service>().greet())
        assertEquals("This is test", test.get<Service>().greet())
    }

    @Test
    fun `test getMany for resolving multiple implementation of an interface`() {
        class Foo : Greeter {
            override fun hello(): String = "foo"
        }

        class Bar : Greeter {
            override fun hello(): String = "bar"
        }

        val capsule = Capsule {
            register { Foo() }
            register { Bar() }
        }

        val greeters: List<Greeter> = capsule.getMany()
        assertEquals(2, greeters.size)
    }

    @Test
    fun `test implicit inherited priority`() {
        val first = Capsule {
            register<Greeter> {
                object : Greeter {
                    override fun hello(): String = "1"
                }
            }
        }

        val second = Capsule(first) {
            register<Greeter> {
                object : Greeter {
                    override fun hello(): String = "2"
                }
            }
        }

        val third = Capsule(second) {
            register<Greeter> {
                object : Greeter {
                    override fun hello(): String = "3"
                }
            }
        }

        val greeter: Greeter = third.get()
        assertEquals("3", greeter.hello())
        assertEquals(listOf("3", "2", "1"), third.getMany<Greeter>().map { it.hello() })
    }

    @Test
    fun `test only needed dependency in created on call to get()`() {
        val parent = Capsule {
            register<Greeter> {
                error("This should not be invoked")
            }
        }

        val child = Capsule(parent) {
            register<Greeter> {
                object : Greeter {
                    override fun hello(): String = "foo"
                }
            }
        }

        val greeter: Greeter = child.get()
        assertEquals("foo", greeter.hello())
    }

    @Test
    fun `creating a new inheriting capsule should invoke a reset of instantiated types`() {
        val parent = Capsule {
            register<Greeter> { FirstImpl() }
            register { GreeterService(get()) }
        }

        assertEquals("first", parent.get<GreeterService>().greet())

        val child = Capsule(parent) {
            register<Greeter> { SecondImpl() }
        }

        assertEquals("second", child.get<GreeterService>().greet())
    }

    @Test
    fun `DependencyException should provide context on which class creation that failed`() {
        try {
            val capsule = Capsule {}
            capsule.get<GrandParent>()
            assertTrue(false)
        } catch (e: DependencyException) {
            val expected = listOf(
                "io.nexure.capsule.GrandParent",
                "io.nexure.capsule.Parent",
                "io.nexure.capsule.Child",
                "double"
            )
            assertEquals(expected, e.children())
        }
    }

    @Test
    fun `DependencyException should provide context on which class creation that failed from first missing class`() {
        try {
            val capsule = Capsule {
                GrandParent(get())
            }
            capsule.get<GrandParent>()
            assertTrue(false)
        } catch (e: DependencyException) {
            val expected = listOf(
                "io.nexure.capsule.Parent",
                "io.nexure.capsule.Child",
                "double"
            )
            assertEquals(expected, e.children())
        }
    }

    @Test
    fun `should not try to automatically instantiate implicit forbidden classes`() {
        data class StringWrapper(val foo: String)
        val capsule = Capsule()
        val exception: Throwable = assertFails { capsule.get<StringWrapper>() }
        assertTrue(exception is DependencyException)
    }
}

class Child(val double: Double)
class Parent(val child: Child)
class GrandParent(val parent: Parent)

interface Greeter {
    fun hello(): String
}

class GreeterService(private val greeter: Greeter) {
    fun greet(): String = greeter.hello()
}

class FirstImpl : Greeter {
    override fun hello(): String = "first"
}

class SecondImpl : Greeter {
    override fun hello(): String = "second"
}
