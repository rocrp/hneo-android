package dev.rocry.hneo.ui.eink

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Volume keys as a page transport.
 *
 * A surface that can turn pages *claims* the keys while it is on screen; the
 * Activity asks whether anyone has claimed them. Nothing writes Activity fields
 * from composition to arrange this.
 */
class VolumeKeyTransport {
    private val _events = MutableSharedFlow<PageDirection>(extraBufferCapacity = 1)
    val events: SharedFlow<PageDirection> = _events.asSharedFlow()

    private val claims = AtomicInteger(0)

    val isClaimed: Boolean get() = claims.get() > 0

    /** Held for as long as a surface is on screen. */
    fun claim(): Claim {
        claims.incrementAndGet()
        return Claim { claims.decrementAndGet() }
    }

    /** True when the key was consumed as a page turn. */
    fun onKeyDown(keyCode: Int): Boolean {
        if (!isClaimed) return false
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> PageDirection.PREVIOUS
            KeyEvent.KEYCODE_VOLUME_DOWN -> PageDirection.NEXT
            else -> return false
        }
        _events.tryEmit(direction)
        return true
    }

    fun interface Claim {
        fun release()
    }
}

val LocalVolumeKeyTransport = staticCompositionLocalOf { VolumeKeyTransport() }

/** Claims the volume keys for as long as this composable is on screen. */
@Composable
fun VolumeKeyPaging(onPage: (PageDirection) -> Unit) {
    val transport = LocalVolumeKeyTransport.current
    val currentOnPage by rememberUpdatedState(onPage)

    DisposableEffect(transport) {
        val claim = transport.claim()
        onDispose { claim.release() }
    }

    LaunchedEffect(transport) {
        transport.events.collect { currentOnPage(it) }
    }
}
