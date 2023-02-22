package io.nexure.capsule

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        class Serializer()
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
            register { Serializer() }
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
}

interface Greeter {
    fun hello(): String
}
