package com.lumina.pdfai.util

/**
 * A single-slot bridge between [com.lumina.pdfai.MainActivity] and whichever
 * screen currently wants to consume the hardware volume rocker.
 *
 * Android dispatches `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN` to the
 * activity *before* any window callback, and a `View` tag is never consulted,
 * so the only reliable seam is `Activity.onKeyDown`. Screens register a
 * handler while they are visible and null it out again in `onDispose`, which
 * keeps the shortcut from leaking into other destinations.
 *
 * The handler returns `true` when it has handled the event; `false` lets the
 * activity fall back to the system volume behaviour.
 */
object VolumeKeyBus {

    /** Current consumer, or `null` when volume keys should adjust the volume. */
    @Volatile
    var handler: ((keyCode: Int) -> Boolean)? = null

    /** Registers [handler] and returns the previous one so it can be restored. */
    fun register(handler: (keyCode: Int) -> Boolean): ((keyCode: Int) -> Boolean)? {
        val previous = this.handler
        this.handler = handler
        return previous
    }

    /** Removes [handler] if it is still the active one. */
    fun unregister(handler: (keyCode: Int) -> Boolean) {
        if (this.handler === handler) this.handler = null
    }

    fun clear() {
        handler = null
    }
}
