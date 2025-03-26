package com.plusinfosys.videotrimmerkotlin.customViews

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock

class UiThreadExecutor {

    companion object {

        private val TOKENS = HashMap<String, Token>()
        private val HANDLER: Handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                val callback = msg.callback
                if (callback != null) {
                    callback.run()
                    decrementToken(msg.obj as Token)
                } else {
                    super.handleMessage(msg)
                }
            }
        }


        fun runTask(id: String, task: Runnable?, delay: Long) {
            if ("" == id) {
                HANDLER.postDelayed(task!!, delay)
                return
            }
            val time = SystemClock.uptimeMillis() + delay
            HANDLER.postAtTime(task!!, nextToken(id), time)
        }

        private fun nextToken(id: String): Token {
            synchronized(TOKENS) {
                var token = TOKENS[id]
                if (token == null) {
                    token = Token(id)
                    TOKENS[id] = token
                }
                ++token.runnablesCount
                return token
            }
        }

        private fun decrementToken(token: Token) {
            synchronized(TOKENS) {
                if (--token.runnablesCount == 0) {
                    val id = token.id
                    val old = TOKENS.remove(id)
                    if (old !== token && old != null) {
                        // a runnable finished after cancelling, we just removed a
                        // wrong token, lets put it back
                        TOKENS[id] = old
                    }
                }
            }
        }

    }

    fun cancelAll(id: String?) {
        var token: Token?
        synchronized(TOKENS) {
            token = TOKENS.remove(id)
        }
        if (token == null) {
            // nothing to cancel
            return
        }
        HANDLER.removeCallbacksAndMessages(token)
    }

    private class Token(val id: String) {
        var runnablesCount = 0
    }

}