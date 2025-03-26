package com.plusinfosys.videotrimmerkotlin.Utils

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class BackgroundExecutor {
    companion object{

        private val DEFAULT_EXECUTOR: Executor = Executors.newScheduledThreadPool(2 * Runtime.getRuntime().availableProcessors())
        private val TASKS: ArrayList<Task> = ArrayList()
        val CURRENT_SERIAL = ThreadLocal<String>()
        private val executor = DEFAULT_EXECUTOR

        private fun take(serial: String): Task? {
            for (i in TASKS.indices) {
                val task = TASKS[i]
                if (serial == task.serial) {
                    TASKS.removeAt(i)
                    return task
                }
            }
            return null
        }

        fun execute(task: Task) {
            var future: Future<*>? = null
            if (task.serial == null || !hasSerialRunning(task.serial!!)) {
                task.executionAsked = true
                future = directExecute(task, task.remainingDelay)
            }
            if ((task.id != null || task.serial != null) && !task.managed.get()) {
                task.future = future
                TASKS.add(task)
            }
        }

        private fun hasSerialRunning(serial: String): Boolean {
            for (task in TASKS) {
                if (task.executionAsked && serial == task.serial) {
                    return true
                }
            }
            return false
        }


        private fun directExecute(runnable: Runnable, delay: Long): Future<*>? {
            var future: Future<*>? = null
            if (delay > 0) {
                future = if (executor is ScheduledExecutorService) {
                    executor.schedule(runnable, delay, TimeUnit.MILLISECONDS)
                } else {
                    throw IllegalArgumentException("The executor set does not support scheduling")
                }
            } else {
                if (executor is ExecutorService) {
                    future = executor.submit(runnable)
                } else {
                    executor.execute(runnable)
                }
            }
            return future
        }

        fun cancelAll(id: String, mayInterruptIfRunning: Boolean) {
            for (i in TASKS.indices.reversed()) {
                val task = TASKS[i]
                if (id == task.id) {
                    if (task.future != null) {
                        task.future?.cancel(mayInterruptIfRunning)
                        if (!task.managed.getAndSet(true)) {
                            task.postExecute()
                        }
                    } else if (task.executionAsked) {
                        // Log.w(TAG, "A task with id " + task.id + " cannot be cancelled (the executor set does not support it)");
                    } else {
                        TASKS.removeAt(i)
                    }
                }
            }
        }
    }

    abstract class Task protected constructor(var id: String?, delay: Long, serial: String?) :
        Runnable {
        var remainingDelay: Long = 0
        private var targetTimeMillis: Long = 0
        var serial: String?
        var executionAsked = false
        var future: Future<*>? = null
        val managed = AtomicBoolean()

        init {
            if (delay > 0) {
                remainingDelay = delay
                targetTimeMillis = System.currentTimeMillis() + delay
            } else {
                targetTimeMillis = 0L
            }
            this.serial = serial
        }

        override fun run() {
            if (managed.getAndSet(true)) {
                return
            }
            try {
                CURRENT_SERIAL.set(serial)
                execute()
            } finally {
                postExecute()
            }
        }

        protected abstract fun execute()
        fun postExecute() {
            if (id == null && serial == null) {
                return
            }
            CURRENT_SERIAL.set(null)
            synchronized(BackgroundExecutor::class.java) {
                TASKS.remove(this)
                if (serial != null) {
                    val next: Task? = take(serial!!)
                    if (next != null) {
                        if (next.remainingDelay != 0L) {
                            next.remainingDelay = max(
                                0.0,
                                (targetTimeMillis - System.currentTimeMillis()).toDouble()
                            )
                                .toLong()
                        }
                        execute(next)
                    }
                }
            }
        }
    }

}