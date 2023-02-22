package io.nexure.capsule

import java.lang.reflect.Constructor
import java.util.LinkedList

@Suppress("UNCHECKED_CAST")
open class Capsule
private constructor(
    private val dependencies: List<Dependency> = LinkedList()
) {
    constructor(): this(LinkedList())

    inline fun <reified T : Any> get(): T = get(T::class.java)
    inline fun <reified T : Any> tryGet(): T? = tryGet(T::class.java)

    fun <T : Any> tryGet(clazz: Class<T>): T? =
        getMany(clazz).firstOrNull() ?: resolveImplicit(clazz)

    fun <T : Any> get(clazz: Class<T>): T =
        tryGet(clazz) ?: throw DependencyException(clazz)

    private fun <T : Any> resolveImplicit(clazz: Class<T>): T? {
        val constructor: Constructor<*> =
            clazz.constructors.minByOrNull { it.parameterCount } ?: return null
        val parameters: Array<*> = try {
            constructor.parameters.map { get(it.type) }.toTypedArray()
        } catch (e: DependencyException) {
            throw DependencyException(clazz, e)
        }
        return constructor.newInstance(*parameters) as? T
    }

    inline fun <reified T : Any> getMany(): List<T> = getMany(T::class.java)

    fun <T : Any> getMany(clazz: Class<T>): List<T> {
        val key: String = classKey(clazz)
        println("Looking for $key")
        return dependencies
            .filter { (it.key == key).also { bool -> println("${it.key} == $key => $bool") } }
            .onEach { "Found match ${it.key}" }
            .map { it.getInstance() as T }.toList()
    }

    class Configuration
    internal constructor(
        internal val dependencies: LinkedList<Dependency> = LinkedList()
    ) : Capsule(dependencies) {
        inline fun <reified T : Any> register(noinline setup: () -> T) {
            val clazz: Class<T> = T::class.java
            register(clazz, setup)
            clazz.interfaces.forEach {
                register(it, setup)
            }
        }

        fun <T : Any> register(clazz: Class<out T>, setup: () -> T) {
            val dependency = Dependency.fromClass(clazz, setup)
            dependencies.push(dependency)
        }
    }

    companion object {
        operator fun invoke(vararg parents: Capsule, config: Configuration.() -> Unit): Capsule {
            val dependencies: List<Dependency> = parents.map { it.dependencies }.flatten()
            val moduleConfig = Configuration().apply(config)
            return Capsule(moduleConfig.dependencies + dependencies)
        }
    }
}

internal class Dependency
private constructor(
    val key: String,
    constructor: () -> Any,
) {
    private val instance: LazyValue<Any> = LazyValue(constructor)

    fun getInstance(): Any = instance()

    override fun toString(): String = key

    companion object {
        fun fromClass(clazz: Class<*>, constructor: () -> Any): Dependency {
            return Dependency(key = classKey(clazz), constructor = constructor)
        }
    }
}

private fun classKey(clazz: Class<*>): String = clazz.canonicalName ?: clazz.descriptorString()

internal class DependencyException(
    val clazz: Class<*>,
    override val cause: DependencyException? = null
) : Exception() {
    override val message: String = if (cause != null) {
        "Unable to provide dependency for class $clazz: \n\t${cause.message}"
    } else {
        "Unable to provide dependency for class $clazz"
    }
}
