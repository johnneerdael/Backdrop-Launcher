package nl.neerdael.backdrop_launcher.composebenchmark

import nl.neerdael.backdrop_launcher.nativebenchmark.*
import org.junit.Assert.*
import org.junit.Test

class HomeItemKeysTest {
    @Test fun identitiesSurviveReorderingAndDuplicatesStayUnique() {
        val a = AppTile("a", "A", null)
        val b = AppTile("b", "B", null)
        val before = HomeItemKeys(listOf(AppRow("One", listOf(a, b, a)), AppRow("Two", listOf(b)), AppRow("One", emptyList())))
        val after = HomeItemKeys(listOf(AppRow("Two", listOf(b)), AppRow("One", listOf(b, a, a)), AppRow("One", emptyList())))
        assertEquals(3, before.rows.toSet().size)
        assertEquals(before.rows[0], after.rows[1])
        assertEquals(before.rows[1], after.rows[0])
        assertEquals(3, before.apps[0].toSet().size)
        assertEquals(before.apps[0][0], after.apps[1][1])
        assertEquals(before.apps[0][1], after.apps[1][0])
        assertEquals(before.apps[0][2], after.apps[1][2])
    }
}
