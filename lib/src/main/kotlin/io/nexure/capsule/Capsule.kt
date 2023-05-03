package io.nexure.capsule

import java.lang.reflect.Constructor

private const val DEFAULT_PRIORITY: Int = Int.MAX_VALUE

@Suppress("UNCHECKED_CAST")
open class Capsule
private constructor(
    private val priority: Int = DEFAULT_PRIORITY,
    private val dependencies: Registry = Registry()
) : DependencyProvider {
    constructor(): this(Int.MAX_VALUE, Registry())

    override fun <T : Any> tryGet(clazz: Class<T>): T? = resolveExplicit(clazz) ?: resolveImplicit(clazz)

    private fun <T : Any> resolveExplicit(clazz: Class<T>): T? =
        getDependencies(clazz).firstOrNull()?.let { it.getInstance(this) as T }

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

    private fun <T: Any> getDependencies(clazz: Class<T>): List<Dependency> = dependencies.getDependencies(Key(clazz))

    class Configuration
    internal constructor(
        private val priority: Int,
        internal val dependencies: Registry,
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
            dependencies.add(dependency)
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
            val dependencies: Registry = parents.map { it.dependencies }.fold(Registry(), Registry::plus)
            val parentsPriority: Int = parents.minOfOrNull { it.priority } ?: DEFAULT_PRIORITY
            val priority: Int = parentsPriority - 1
            val moduleConfig = Configuration(priority, dependencies).apply(config)
            return Capsule(priority, moduleConfig.dependencies)
        }
    }
}

data class Key(private val clazz: Class<*>) {
    override fun toString(): String = clazz.canonicalName ?: clazz.descriptorString()
}

internal class DependencyException(
    val key: Key,
    override val cause: Exception? = null
) : Exception() {
    constructor(clazz: Class<*>, cause: Exception? = null) : this(Key(clazz), cause)

    fun children(): List<String> {
        val childClasses: List<String> =
            if (cause is DependencyException) cause.children() else emptyList()

        return listOf(key.toString()) + childClasses
    }

    override val message: String =
        "Unable to provide dependency for class, " +
            "since no explicit implementation was registered in chain: ${children()}"
}
