package com.dietician.shared.ui.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NutrientCatalogTest {

    @Test
    fun `catalog ships skeleton with at least 20 entries (Plan-7 backfills to 84)`() {
        assertTrue(NutrientCatalog.all.size >= 20, "Batch B target: skeleton >= 20")
    }

    @Test
    fun `every nutrient id is unique`() {
        val ids = NutrientCatalog.all.map { it.id }
        assertEquals(ids.toSet().size, ids.size, "duplicate id in catalog: $ids")
    }

    @Test
    fun `byId returns macros`() {
        assertNotNull(NutrientCatalog.byId("kcal"))
        assertNotNull(NutrientCatalog.byId("protein"))
        assertNotNull(NutrientCatalog.byId("carbs"))
        assertNotNull(NutrientCatalog.byId("fat"))
    }

    @Test
    fun `byId unknown returns null`() {
        assertNull(NutrientCatalog.byId("nonexistent-nutrient"))
    }

    @Test
    fun `every nutrient has non-blank EN + RO display name`() {
        for (n in NutrientCatalog.all) {
            assertTrue(n.displayNameEn.isNotBlank(), "missing displayNameEn for ${n.id}")
            assertTrue(n.displayNameRo.isNotBlank(), "missing displayNameRo for ${n.id}")
        }
    }

    @Test
    fun `RO and EN names differ for at least the macros (not stub copies)`() {
        val kcal = NutrientCatalog.byId("kcal")!!
        val protein = NutrientCatalog.byId("protein")!!
        assertTrue(kcal.displayNameEn != kcal.displayNameRo, "kcal RO = EN")
        assertTrue(protein.displayNameEn != protein.displayNameRo, "protein RO = EN")
    }

    @Test
    fun `byCategory MACRO returns macros`() {
        val macros = NutrientCatalog.byCategory(NutrientCategory.MACRO)
        val ids = macros.map { it.id }
        assertTrue("kcal" in ids)
        assertTrue("protein" in ids)
        assertTrue("carbs" in ids)
        assertTrue("fat" in ids)
    }

    @Test
    fun `default RDAs are positive for non-stub macros`() {
        for (n in NutrientCatalog.all) {
            assertTrue(n.defaultRdaForVictor > 0.0, "RDA must be > 0 for ${n.id}")
        }
    }

    @Test
    fun `every category has at least one entry in skeleton`() {
        for (cat in NutrientCategory.values()) {
            val entries = NutrientCatalog.byCategory(cat)
            assertTrue(entries.isNotEmpty(), "no skeleton entry for category $cat")
        }
    }
}
