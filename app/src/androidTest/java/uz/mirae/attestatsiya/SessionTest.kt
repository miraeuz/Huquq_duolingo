package uz.mirae.attestatsiya

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionTest {
    @Test fun launchDashboard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
    @Test fun quotasResumeAndFinish() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("progress.db")
        var store = Store(context)
        try {
            for (size in listOf(25,50)) {
                val run = store.start(size)
                val questions = store.items(run)
                assertEquals(size,questions.distinctBy { it.id }.size)
                val counts = questions.groupingBy { it.group }.eachCount()
                assertEquals(if(size==25) listOf(17,3,5) else listOf(35,5,10), codes.map(counts::getValue))
                assertEquals(run,store.start(size))
                assertEquals(0,store.advance(run,0))
                store.select(run,0,"B")
                store.close()
                store = Store(context)
                assertEquals("B",store.draft(run,0))
                assertEquals(questions.map { it.id },store.items(run).map { it.id })
                for ((index,q) in questions.withIndex()) {
                    store.select(run,index,q.key)
                    store.answer(run,index,q.key)
                    store.answer(run,index,"A")
                    if(index<size-1) {
                        assertEquals(index+1,store.advance(run,index))
                        assertEquals(index+1,store.advance(run,index))
                    }
                }
                assertTrue(store.runs().first { it.id==run }.done)
                assertTrue(store.items(run).all { it.pick==it.key })
            }
        } finally { store.close() }
    }
}
