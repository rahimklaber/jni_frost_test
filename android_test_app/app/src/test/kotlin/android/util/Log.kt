@file:JvmName("Log")

package android.util

fun d(tag: String, msg: String, t: Throwable): Int {
    println("debug: $tag: $msg")
    return 0
}

fun d(tag: String, msg: String): Int {
    println("debug: $tag: $msg")
    return 0
}

