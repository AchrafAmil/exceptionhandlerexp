package com.example.exceptionhandlerexp.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

val startTs: Long by lazy { System.currentTimeMillis() }
val elapsedMs: Long
    get() = System.currentTimeMillis() - startTs

val MILLIS_BEFORE_MOVING_TO_SECOND_FRAGMENT = 10_000L

@Suppress("SameParameterValue")
class MainViewModel : ViewModel() {

    private val TAG = TAG_PREFIX + this::class.java.simpleName

    val handler = CoroutineExceptionHandler { coroutineContext, throwable ->
        Log.i(TAG, "$elapsedMs Oups, exception $throwable")
    }

    init {
        viewModelScope.safeLaunch {
            val logTag = "$TAG SIMPLE"
            Log.i(logTag, "$elapsedMs started simple 5 secs coroutine 🙌")
            try {
                delay(5_000)
                Log.i(logTag, "$elapsedMs coroutine still running after 5 secs")
            } finally {
                Log.i(logTag, "$elapsedMs coroutine finished")
            }
        }

        viewModelScope.safeLaunch {
            val logTag = "$TAG BLOCKING"
            Log.i(logTag, "$elapsedMs started infinite until cancelled coroutine ♾")
            infiniteUntilCancelled(logTag)
        }

        viewModelScope.safeLaunch(handler) {
            val logTag = "$TAG THROW"
            Log.i(logTag, "$elapsedMs scheduled exception throwing in 2 secs 💣")
            delay(2_000)
            throw IOException("$logTag at $elapsedMs")
        }

        viewModelScope.safeLaunch (CoroutineExceptionHandler { _, t ->
            Log.i("$TAG FLOW", "$elapsedMs exception handled by custom handler !")
        }) {
            val logTag = "$TAG FLOW"
            Log.i(logTag, "$elapsedMs collecting one-second-ticking flow 🕐")
            tickingFlow(logTag, throwAtTickNumber = 3)
                    .collect {
                        Log.i(logTag, "$elapsedMs collecting flow tick number $it")
                    }
        }
    }

    private suspend fun infiniteUntilCancelled(logTag: String) = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine<Unit> { cancellableContinuation ->
            cancellableContinuation.invokeOnCancellation { Log.i(logTag, "$elapsedMs cancellation detected ⏹") }

            runBlocking(coroutineContext) {
                val asyncJob = async {
                    Log.i(logTag, "$elapsedMs starting async")
                    while (true) {
                        delay(1_000)
                        Log.i(logTag, "$elapsedMs async still running (isActive = $isActive)")
                    }
                }
                while (isActive) asyncJob.join() // blocking until cancelled
                Log.i(logTag, "Oh, job not active anymore")
            }
        }
    }

    private fun tickingFlow(logTag: String, throwAtTickNumber: Int) = (0..100).asFlow()
            .map { tickCount ->
                Log.i(logTag, "$elapsedMs flow ticking number $tickCount")
                if (tickCount == throwAtTickNumber) {
                    Log.i(logTag, "$elapsedMs trap in flow, throwing exception 😈")
                    throw IOException(logTag)
                }
                delay(1_000)
                tickCount
            }
            .onCompletion { Log.i(logTag, "$elapsedMs flow completed") }
}

fun CoroutineScope.safeLaunch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
): Job {
    return (this + noOpExceptionHandler).launch(context, start, block)
}

private val noOpExceptionHandler = CoroutineExceptionHandler { _, e ->
    Log.i("$TAG_PREFIX NOOP", "$elapsedMs noOpExceptionHandler handling $e")
}