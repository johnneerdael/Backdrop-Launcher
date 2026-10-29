package nl.neerdael.backdrop_launcher.composebenchmark

import org.junit.Assert.*
import org.junit.Test

class ImageSubscriptionTest {
    @Test fun unchangedVisibleBoundsDoNotRestartWorkAndOldImagesCannotPaintAReplacement() {
        val starts = mutableListOf<String>()
        val cancellations = mutableListOf<String>()
        val callbacks = mutableMapOf<String, (String?) -> Unit>()
        var displayed: String? = null
        val binding = ImageSubscription<String, String>(subscribe = { request, deliver ->
            starts.add(request)
            callbacks[request] = deliver
            { cancellations.add(request); Unit }
        }, display = { displayed = it })
        binding.update("old")
        binding.update("old")
        assertEquals(listOf("old"), starts)
        callbacks.getValue("old")("old bitmap")
        assertEquals("old bitmap", displayed)
        binding.update("new")
        assertEquals(listOf("old"), cancellations)
        assertNull(displayed)
        callbacks.getValue("old")("stale bitmap")
        assertNull(displayed)
        callbacks.getValue("new")("fresh bitmap")
        assertEquals("fresh bitmap", displayed)
        binding.update(null)
        assertEquals(listOf("old", "new"), cancellations)
        callbacks.getValue("new")("late after pause")
        assertNull(displayed)
    }

    @Test fun visibleSubscriptionCanBeRenewedAfterSynchronousCacheDeliveryAndViewportExit() {
        var starts = 0
        var displayed: String? = null
        val binding = ImageSubscription<String, String>(subscribe = { request, deliver ->
            starts++
            deliver(request)
            val cancel = {}
            cancel
        }, display = { displayed = it })
        binding.update(null)
        assertEquals(0, starts)
        binding.update("visible")
        assertEquals("visible", displayed)
        binding.update(null)
        assertNull(displayed)
        binding.update("visible")
        assertEquals(2, starts)
        assertEquals("visible", displayed)
    }
}
