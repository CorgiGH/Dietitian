@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.PaperResult
import com.dietician.shared.ui.data.PaperSearchOutcome
import com.dietician.shared.ui.data.PaperSearchRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaperSearchViewModelTest {

    private class FakeRepo(private var outcome: PaperSearchOutcome) : PaperSearchRepository {
        val queries = mutableListOf<Pair<String, Set<String>>>()
        fun setOutcome(o: PaperSearchOutcome) {
            outcome = o
        }
        override suspend fun search(query: String, corpora: Set<String>): PaperSearchOutcome {
            queries += query to corpora
            return outcome
        }
    }

    private fun pr(id: String, title: String, score: Double = 0.5) = PaperResult(
        id = id,
        title = title,
        abstractSnippet = "Snippet for $title",
        score = score,
    )

    @Test
    fun `initial state empty + all corpora selected`() = runTest {
        val vm = PaperSearchViewModel(repo = FakeRepo(PaperSearchOutcome.Empty))
        assertEquals("", vm.state.value.query)
        assertTrue(vm.state.value.results.isEmpty())
        // Default corpora: all four
        assertEquals(setOf("papers", "supplements", "recipes", "foods"), vm.state.value.corpora)
    }

    @Test
    fun `submitQuery populates results from repo`() = runTest {
        val results = listOf(pr("1", "Protein synthesis"), pr("2", "MPS rate"))
        val repo = FakeRepo(PaperSearchOutcome.Hits(results))
        val vm = PaperSearchViewModel(repo = repo, coroutineScope = this)
        vm.onQueryChange("mps")
        vm.submitQuery()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.results.size)
        assertEquals("Protein synthesis", vm.state.value.results[0].title)
        assertEquals(false, vm.state.value.embedNotImplemented)
        assertEquals(1, repo.queries.size)
        assertEquals("mps", repo.queries.first().first)
    }

    @Test
    fun `submitQuery 501 → empty results + notImplemented flag set`() = runTest {
        val vm = PaperSearchViewModel(
            repo = FakeRepo(PaperSearchOutcome.NotImplemented),
            coroutineScope = this,
        )
        vm.onQueryChange("test")
        vm.submitQuery()
        advanceUntilIdle()
        assertTrue(vm.state.value.results.isEmpty())
        assertTrue(vm.state.value.embedNotImplemented)
    }

    @Test
    fun `submitQuery empty result keeps results empty + sets searched=true`() = runTest {
        val vm = PaperSearchViewModel(
            repo = FakeRepo(PaperSearchOutcome.Empty),
            coroutineScope = this,
        )
        vm.onQueryChange("nomatch")
        vm.submitQuery()
        advanceUntilIdle()
        assertTrue(vm.state.value.results.isEmpty())
        assertTrue(vm.state.value.searched)
        assertFalse(vm.state.value.embedNotImplemented)
    }

    @Test
    fun `toggleCorpus flips membership`() = runTest {
        val vm = PaperSearchViewModel(repo = FakeRepo(PaperSearchOutcome.Empty))
        vm.toggleCorpus("supplements")
        assertFalse(vm.state.value.corpora.contains("supplements"))
        vm.toggleCorpus("supplements")
        assertTrue(vm.state.value.corpora.contains("supplements"))
    }

    @Test
    fun `submitQuery passes selected corpora to repo`() = runTest {
        val repo = FakeRepo(PaperSearchOutcome.Empty)
        val vm = PaperSearchViewModel(repo = repo, coroutineScope = this)
        vm.toggleCorpus("recipes") // remove recipes
        vm.toggleCorpus("foods") // remove foods
        vm.onQueryChange("creatine")
        vm.submitQuery()
        advanceUntilIdle()
        assertEquals(setOf("papers", "supplements"), repo.queries.first().second)
    }

    @Test
    fun `blank query no-ops`() = runTest {
        val repo = FakeRepo(PaperSearchOutcome.Empty)
        val vm = PaperSearchViewModel(repo = repo, coroutineScope = this)
        vm.onQueryChange("  ")
        vm.submitQuery()
        advanceUntilIdle()
        assertEquals(0, repo.queries.size)
    }
}
