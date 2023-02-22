package io.nexure.capsule

import java.util.concurrent.Semaphore

class LazyValue<T>(private val creation: () -> T) {
    private val semaphore = Semaphore(1)
    private var value: T? = null

    fun get(): T {
        if (semaphore.tryAcquire()) {
            this.value = creation()
        }
        return this.value!!
    }

    operator fun invoke(): T = get()
}

inline fun <reified T : Any> lazy(noinline creation: () -> T): LazyValue<T> = LazyValue(creation)
