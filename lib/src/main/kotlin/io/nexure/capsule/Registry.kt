package io.nexure.capsule

import java.util.LinkedList

internal class Registry(
    private val dependencies: MutableMap<Key, MutableList<Dependency>> = HashMap()
) {
    fun getDependencies(key: Key): List<Dependency> = dependencies[key]?.sorted() ?: emptyList()

    fun add(dependency: Dependency) {
        dependencies.compute(dependency.key) { _: Key, value: MutableList<Dependency>? ->
            val list: MutableList<Dependency> = value ?: LinkedList()
            list.add(dependency)
            list
        }
    }

    operator fun plus(other: Registry): Registry {
        val dependencies: MutableMap<Key, MutableList<Dependency>> = HashMap()

        sequenceOf(this.dependencies.values, other.dependencies.values)
            .flatten()
            .flatten()
            .map { it.copy() }
            .groupByTo(dependencies) { it.key }

        return Registry(dependencies)
    }
}
