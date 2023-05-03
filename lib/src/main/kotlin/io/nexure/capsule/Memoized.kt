package io.nexure.capsule

import java.util.concurrent.Semaphore

internal class Memoized<T> {
    private val semaphore = Semaphore(1)
    private var value: T? = null

    fun getOnce(creation: () -> T): T {
        if (semaphore.tryAcquire()) {
            this.value = creation()
        }
        return this.value!!
    }
}

