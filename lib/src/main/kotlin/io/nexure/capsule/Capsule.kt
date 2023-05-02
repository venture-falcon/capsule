package io.nexure.capsule

import mu.KotlinLogging
import java.lang.reflect.Constructor
import java.util.LinkedList

private const val DEFAULT_PRIORITY: Int = Int.MAX_VALUE

private val log = KotlinLogging.logger {}

interface DependencyProvider {
    fun <T : Any> tryGet(clazz: Class<T>): T?
    fun <T : Any> get(clazz: Class<T>): T = tryGet(clazz) ?: throw DependencyException(clazz)
    fun <T : Any> getMany(clazz: Class<T>): List<T>
}

inline fun <reified T : Any> DependencyProvider.get(): T = get(T::class.java)
inline fun <reified T : Any> DependencyProvider.tryGet(): T? = tryGet(T::class.java)
inline fun <reified T : Any> DependencyProvider.getMany(): List<T> = getMany(T::class.java)

@Suppress("UNCHECKED_CAST")
open class Capsule
private constructor(
    private val priority: Int = DEFAULT_PRIORITY,
    private val dependencies: List<Dependency> = LinkedList()
) : DependencyProvider {
    constructor(): this(Int.MAX_VALUE, LinkedList())

    override fun <T : Any> tryGet(clazz: Class<T>): T? =
        getDependencies(clazz).firstOrNull()?.let { it.getInstance(this) as T } ?: resolveImplicit(clazz)

    private fun <T : Any> resolveImplicit(clazz: Class<T>): T? {
        if (clazz in forbiddenImplicit) {
            return null
        }
        val constructor: Constructor<*> =
            clazz.constructors.minByOrNull { it.parameterCount } ?: return null
        val parameters: Array<*> = try {
            constructor.parameters.map { get(it.type) }.toTypedArray()
        } catch (e: DependencyException) {
            throw DependencyException(clazz, e)
        }
        return constructor.newInstance(*parameters) as? T
    }

    override fun <T : Any> getMany(clazz: Class<T>): List<T> = getDependencies(clazz).map { it.getInstance(this) as T }

    private fun <T: Any> getDependencies(clazz: Class<T>): List<Dependency> {
        val key: String = classKey(clazz)
        return dependencies.filter { (it.key == key) }.sorted()
    }

    class Configuration
    internal constructor(
        private val priority: Int,
        internal val dependencies: LinkedList<Dependency>,
    ) : Capsule(priority, dependencies) {
        inline fun <reified T : Any> register(noinline setup: DependencyProvider.() -> T) {
            val clazz: Class<T> = T::class.java
            register(clazz, setup)
            clazz.interfaces.forEach {
                register(it, setup)
            }
        }

        fun <T : Any> register(clazz: Class<out T>, setup: DependencyProvider.() -> T) {
            val dependency = Dependency.fromClass(clazz, priority, setup)
            dependencies.push(dependency)
        }
    }

    companion object {
        /**
         * These are classes which should never be implicitly instantiated by using an automatically resolved
         * constructor, since this is almost certainly an oversight and an error.
         */
        private val forbiddenImplicit: Set<Class<*>> = setOf(
            String::class.java,
            ByteArray::class.java,
            Array::class.java
        )

        operator fun invoke(
            vararg parents: Capsule,
            config: Configuration.() -> Unit
        ): Capsule {
            val dependencies: LinkedList<Dependency> = parents
                .map { it.dependencies }
                .flatten()
                .map { it.copy() }
                .let { LinkedList(it) }

            val parentsPriority: Int = parents.minOfOrNull { it.priority } ?: DEFAULT_PRIORITY
            val priority: Int = parentsPriority - 1
            val moduleConfig = Configuration(priority, dependencies).apply(config)
            log.debug { "Capsule created with dependencies: ${moduleConfig.dependencies}" }
            return Capsule(priority, moduleConfig.dependencies)
        }
    }
}

internal class Dependency
private constructor(
    val key: String,
    val priority: Int,
    private val constructor: DependencyProvider.() -> Any,
): Comparable<Dependency> {
    private val instance: Memoized<Any> = Memoized()

    fun getInstance(provider: DependencyProvider): Any {
        return try {
            instance.getOnce {
                provider.constructor()
            }
        } catch (e: Exception) {
            throw DependencyException(key, e)
        }
    }

    fun copy(): Dependency = Dependency(key, priority, constructor)

    override fun toString(): String = key

    override fun compareTo(other: Dependency): Int = this.priority.compareTo(other.priority)

    companion object {
        fun fromClass(clazz: Class<*>, priority: Int, constructor: DependencyProvider.() -> Any): Dependency {
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
