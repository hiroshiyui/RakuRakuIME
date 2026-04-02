package org.ghostsinthelab.app.rakurakuime

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.ghostsinthelab.app.rakurakuime.data.CinParser
import org.ghostsinthelab.app.rakurakuime.data.ImeDatabase
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test to verify that the EZ Input Method dictionary
 * is correctly imported and contains key mappings.
 */
@RunWith(AndroidJUnit4::class)
class DictionaryTest {

    private lateinit var db: ImeDatabase

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        db = ImeDatabase.getDatabase(appContext)
    }

    @Test
    fun verifyDictionaryContent() = runBlocking {
        val dao = db.dictionaryDao()
        
        // If empty, trigger import (simulates first run)
        if (dao.count() == 0) {
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            CinParser.parseAndPopulate(appContext, db)
        }

        val count = dao.count()
        assertTrue("Dictionary should have at least 50,000 entries", count > 50000)

        // Verify '這' mapping (z4)
        val z4Candidates = dao.getCharacters("z4")
        assertTrue("Mapping 'z4' should contain '這'", z4Candidates.contains("這"))

        // Verify '姆' mapping (v=)
        val vEqualCandidates = dao.getCharacters("v=")
        assertTrue("Mapping 'v=' should contain '姆'", vEqualCandidates.contains("姆"))
        
        // Verify '山' root (u)
        val uCandidates = dao.getCharacters("u")
        assertTrue("Root 'u' should contain '山'", uCandidates.contains("山"))
    }
}
