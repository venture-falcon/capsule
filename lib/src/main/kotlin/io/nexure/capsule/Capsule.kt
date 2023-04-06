package io.nexure.capsule

import java.lang.reflect.Constructor
import java.util.LinkedList

private const val DEFAULT_PRIORITY: Int = Int.MAX_VALUE

@Suppress("UNCHECKED_CAST")
open class Capsule
private constructor(
    private val priority: Int = DEFAULT_PRIORITY,
    private val dependencies: List<Dependency> = LinkedList()
) {
    constructor(): this(Int.MAX_VALUE, LinkedList())

    inline fun <reified T : Any> get(): T = get(T::class.java)
    inline fun <reified T : Any> tryGet(): T? = tryGet(T::class.java)

    fun <T : Any> tryGet(clazz: Class<T>): T? =
        getDependencies(clazz).firstOrNull()?.let { it.getInstance() as T } ?: resolveImplicit(clazz)

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

    fun <T : Any> getMany(clazz: Class<T>): List<T> = getDependencies(clazz).map { it.getInstance() as T }

    private fun <T: Any> getDependencies(clazz: Class<T>): List<Dependency> {
        val key: String = classKey(clazz)
        return dependencies.filter { (it.key == key) }.sorted()
    }

    class Configuration
    internal constructor(
        private val priority: Int,
        internal val dependencies: LinkedList<Dependency>,
    ) : Capsule(priority, dependencies) {
        inline fun <reified T : Any> register(noinline setup: () -> T) {
            val clazz: Class<T> = T::class.java
            register(clazz, setup)
            clazz.interfaces.forEach {
                register(it, setup)
            }
        }

        fun <T : Any> register(clazz: Class<out T>, setup: () -> T) {
            val dependency = Dependency.fromClass(clazz, priority, setup)
            dependencies.push(dependency)
        }
    }

    companion object {
        operator fun invoke(
            vararg parents: Capsule,
            config: Configuration.() -> Unit
        ): Capsule {
            val dependencies: LinkedList<Dependency> = parents.map { it.dependencies }.flatten().let { LinkedList(it) }
            val parentsPriority: Int = parents.minOfOrNull { it.priority } ?: DEFAULT_PRIORITY
            val priority: Int = parentsPriority - 1
            val moduleConfig = Configuration(priority, dependencies).apply(config)
            return Capsule(priority, moduleConfig.dependencies)
        }
    }
}

internal class Dependency
private constructor(
    val key: String,
    val priority: Int,
    constructor: () -> Any,
): Comparable<Dependency> {
    private val instance: LazyValue<Any> = LazyValue(constructor)

    fun getInstance(): Any {
        return try {
            instance()
        } catch (e: Exception) {
            throw DependencyException(key, e)
        }
    }

    override fun toString(): String = key

    override fun compareTo(other: Dependency): Int = this.priority.compareTo(other.priority)

    companion object {
        fun fromClass(clazz: Class<*>, priority: Int, constructor: () -> Any): Dependency {
            return Dependency(key = classKey(clazz), priority = priority, constructor = constructor)
        }
    }
}

private fun classKey(clazz: Class<*>): String = clazz.canonicalName ?: clazz.descriptorString()

internal class DependencyException(
    val key: String,
    override val cause: Exception? = null
) : Exception() {
    constructor(clazz: Class<*>, cause: Exception? = null) : this(classKey(clazz), cause)

    fun children(): List<String> {
        val childClasses: List<String> =
            if (cause is DependencyException) cause.children() else emptyList()

        return listOf(key) + childClasses
    }

    override val message: String =
        "Unable to provide dependency for class, " +
            "since no explicit implementation was registered in chain: ${children()}"
}
