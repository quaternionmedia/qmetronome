package media.quaternion.qmetronome.visualizers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerRegistryTest {

    @Test
    fun `every visualizer has a unique, non-blank id`() {
        val ids = VisualizerRegistry.all.map { it.id }
        assertTrue("ids must not be blank", ids.all { it.isNotBlank() })
        assertEquals("visualizer ids must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun `next cycles through all visualizers and wraps around`() {
        val first = VisualizerRegistry.all.first()
        var current = first
        repeat(VisualizerRegistry.all.size) {
            current = VisualizerRegistry.next(current)
        }
        // After exactly one full cycle we should be back at the start.
        assertEquals(first.id, current.id)
    }

    @Test
    fun `byId falls back to default for an unknown id`() {
        assertEquals(VisualizerRegistry.default.id, VisualizerRegistry.byId("not-a-real-id").id)
        assertEquals(VisualizerRegistry.default.id, VisualizerRegistry.byId(null).id)
    }

    @Test
    fun `byId finds a known visualizer by its id`() {
        val target = VisualizerRegistry.all.last()
        assertNotNull(VisualizerRegistry.byId(target.id))
        assertEquals(target.id, VisualizerRegistry.byId(target.id).id)
    }
}
