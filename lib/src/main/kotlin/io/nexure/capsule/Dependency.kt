package io.nexure.capsule

internal class Dependency
private constructor(
    val key: Key,
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

    override fun toString(): String = key.toString()

    override fun compareTo(other: Dependency): Int = this.priority.compareTo(other.priority)

    companion object {
        fun fromClass(clazz: Class<*>, priority: Int, constructor: DependencyProvider.() -> Any): Dependency {
            return Dependency(key = Key(clazz), priority = priority, constructor = constructor)
        }
    }
}
