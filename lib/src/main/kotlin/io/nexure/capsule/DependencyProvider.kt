package io.nexure.capsule

interface DependencyProvider {
    fun <T : Any> tryGet(clazz: Class<T>): T?
    fun <T : Any> get(clazz: Class<T>): T = tryGet(clazz) ?: throw DependencyException(clazz)
    fun <T : Any> getMany(clazz: Class<T>): List<T>
}

inline fun <reified T : Any> DependencyProvider.get(): T = get(T::class.java)
inline fun <reified T : Any> DependencyProvider.tryGet(): T? = tryGet(T::class.java)
inline fun <reified T : Any> DependencyProvider.getMany(): List<T> = getMany(T::class.java)
