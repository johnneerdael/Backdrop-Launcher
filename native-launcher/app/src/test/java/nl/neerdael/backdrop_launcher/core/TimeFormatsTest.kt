package nl.neerdael.backdrop_launcher.core

import org.junit.Assert.*
import org.junit.Test

class TimeFormatsTest {
    @Test fun secondsPatternsTickWhileMinuteFormatsAndLiteralsSleep() {
        assertTrue(needsSecondTicks("H:mm:ss", "EEEE d"))
        assertTrue(needsSecondTicks("H:mm", "ss.SSS"))
        assertTrue(needsSecondTicks("H:mm ''ss"))
        assertFalse(needsSecondTicks("H:mm", "EEEE d"))
        assertFalse(needsSecondTicks("H:mm 'seconds'", "'Sunday' d"))
        assertFalse(needsSecondTicks("H:mm 'it''s time'"))
    }
}
