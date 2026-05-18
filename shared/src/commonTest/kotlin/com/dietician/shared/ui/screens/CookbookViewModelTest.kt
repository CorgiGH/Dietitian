package com.dietician.shared.ui.screens

import com.dietician.shared.ui.data.Recipe
import com.dietician.shared.ui.data.RecipeIngestClient
import com.dietician.shared.ui.data.RecipeIngestResult
import com.dietician.shared.ui.data.RecipeReader
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CookbookViewModelTest {

    private class FakeReader(private val recipes: List<Recipe> = emptyList()) : RecipeReader {
        override suspend fun all(): List<Recipe> = recipes
    }

    private class FakeIngest(private val result: RecipeIngestResult) : RecipeIngestClient {
        val calls = mutableListOf<String>()
        override suspend fun ingest(url: String): RecipeIngestResult {
            calls += url
            return result
        }
    }

    private fun recipe(id: String, title: String) = Recipe(
        id = id,
        title = title,
        ingredientsCsv = "",
        servings = 1,
    )

    @Test
    fun `initial state has empty query + empty recipes`() {
        val vm = CookbookViewModel(reader = FakeReader(), ingest = FakeIngest(RecipeIngestResult.Queued))
        assertEquals("", vm.state.value.query)
        assertTrue(vm.state.value.recipes.isEmpty())
        assertFalse(vm.state.value.ingestDialogVisible)
    }

    @Test
    fun `load surfaces recipes from reader`() = runTest {
        val recipes = listOf(recipe("a", "Sarmale"), recipe("b", "Mici"))
        val vm = CookbookViewModel(reader = FakeReader(recipes = recipes), ingest = FakeIngest(RecipeIngestResult.Queued))
        vm.load()
        assertEquals(2, vm.state.value.recipes.size)
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun `query filters local recipes case-insensitive`() = runTest {
        val recipes = listOf(
            recipe("a", "Sarmale cu mămăligă"),
            recipe("b", "Mici"),
            recipe("c", "Tochitură moldovenească"),
        )
        val vm = CookbookViewModel(reader = FakeReader(recipes = recipes), ingest = FakeIngest(RecipeIngestResult.Queued))
        vm.load()
        vm.onQueryChange("sarma")
        val filtered = vm.state.value.filtered.map { it.id }
        assertEquals(listOf("a"), filtered)
    }

    @Test
    fun `empty query shows all recipes`() = runTest {
        val recipes = listOf(recipe("a", "A"), recipe("b", "B"))
        val vm = CookbookViewModel(reader = FakeReader(recipes = recipes), ingest = FakeIngest(RecipeIngestResult.Queued))
        vm.load()
        vm.onQueryChange("nothing-matches")
        vm.onQueryChange("")
        assertEquals(2, vm.state.value.filtered.size)
    }

    @Test
    fun `showIngestDialog + hideIngestDialog toggle`() {
        val vm = CookbookViewModel(reader = FakeReader(), ingest = FakeIngest(RecipeIngestResult.Queued))
        vm.showIngestDialog()
        assertTrue(vm.state.value.ingestDialogVisible)
        vm.hideIngestDialog()
        assertFalse(vm.state.value.ingestDialogVisible)
    }

    @Test
    fun `ingestUrl with 200 shows success toast`() = runTest {
        val ingest = FakeIngest(RecipeIngestResult.Queued)
        val vm = CookbookViewModel(reader = FakeReader(), ingest = ingest)
        vm.onIngestUrlChange("https://example.com/recipe")
        vm.submitIngest()
        assertEquals(1, ingest.calls.size)
        assertEquals("https://example.com/recipe", ingest.calls.first())
        assertNotNull(vm.state.value.toast)
        assertTrue(vm.state.value.toast!!.contains("Queued"))
    }

    @Test
    fun `ingestUrl with 501 shows outbox toast`() = runTest {
        val vm = CookbookViewModel(reader = FakeReader(), ingest = FakeIngest(RecipeIngestResult.NotImplemented))
        vm.onIngestUrlChange("https://example.com/recipe")
        vm.submitIngest()
        assertNotNull(vm.state.value.toast)
        assertTrue(vm.state.value.toast!!.contains("ingest pipeline"))
    }

    @Test
    fun `ingestUrl rejects invalid url`() = runTest {
        val ingest = FakeIngest(RecipeIngestResult.Queued)
        val vm = CookbookViewModel(reader = FakeReader(), ingest = ingest)
        vm.onIngestUrlChange("not a url")
        vm.submitIngest()
        assertEquals(0, ingest.calls.size)
        assertNotNull(vm.state.value.toast)
        assertTrue(vm.state.value.toast!!.lowercase().contains("invalid"))
    }

    @Test
    fun `clearToast nulls toast`() = runTest {
        val vm = CookbookViewModel(reader = FakeReader(), ingest = FakeIngest(RecipeIngestResult.Queued))
        vm.onIngestUrlChange("https://example.com/recipe")
        vm.submitIngest()
        vm.clearToast()
        assertEquals(null, vm.state.value.toast)
    }
}
