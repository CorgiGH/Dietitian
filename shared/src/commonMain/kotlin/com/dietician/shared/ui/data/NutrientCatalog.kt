package com.dietician.shared.ui.data

/**
 * Cronometer-style nutrient catalog (per Round-3 §1.13 pattern 1 + spec §6.4).
 *
 * **Batch B ship: skeleton (~25 entries)** — macros + fiber + key vitamins + key
 * minerals + EAA totals. The full 84-nutrient set (13 vitamins + 17 minerals +
 * 9 EAAs + 6 NEAAs + macros + fiber + n-3/n-6 + glycemic load + ...) lands in
 * Plan-7 corpus ingestion task. UI surface is unchanged when Plan-7 backfills —
 * callers iterate over [NutrientCatalog.all].
 *
 * Default RDAs are tuned for Victor (188cm / 67.5kg lean-bulk active male per
 * UAIC student identity memory). [defaultRdaForVictor] is overridden per-subject
 * via [com.dietician.shared.ui.data.SubjectNutrientOverrides] (Batch D Task 18).
 *
 * Unit strings stay loose (g/mg/μg/IU) — no `kotlinx.measure`-style strong types
 * yet; Plan-7 may upgrade. Display layer just shows `value + unit` as-is.
 */
enum class NutrientUnit(val display: String) {
    G("g"),
    MG("mg"),
    MCG("μg"),
    IU("IU"),
    KCAL("kcal"),
    ;
}

enum class NutrientCategory {
    MACRO,
    FIBER,
    VITAMIN,
    MINERAL,
    AMINO_ACID,
    OTHER,
}

data class Nutrient(
    val id: String,
    val displayNameEn: String,
    val displayNameRo: String,
    val unit: NutrientUnit,
    val category: NutrientCategory,
    val defaultRdaForVictor: Double,
)

object NutrientCatalog {

    /** Skeleton catalog — Batch B. Plan-7 corpus ingestion backfills to full 84. */
    val all: List<Nutrient> = listOf(
        // --- Macros ---
        Nutrient("kcal", "Calories", "Calorii", NutrientUnit.KCAL, NutrientCategory.MACRO, 2700.0),
        Nutrient("protein", "Protein", "Proteine", NutrientUnit.G, NutrientCategory.MACRO, 160.0),
        Nutrient("carbs", "Carbohydrates", "Carbohidrați", NutrientUnit.G, NutrientCategory.MACRO, 350.0),
        Nutrient("fat", "Fat", "Grăsimi", NutrientUnit.G, NutrientCategory.MACRO, 90.0),
        Nutrient("sat_fat", "Saturated fat", "Grăsimi saturate", NutrientUnit.G, NutrientCategory.MACRO, 25.0),
        Nutrient("sugar", "Sugar", "Zahăr", NutrientUnit.G, NutrientCategory.MACRO, 50.0),

        // --- Fiber ---
        Nutrient("fiber", "Fiber", "Fibre", NutrientUnit.G, NutrientCategory.FIBER, 38.0),

        // --- Key vitamins ---
        Nutrient("vit_a", "Vitamin A", "Vitamina A", NutrientUnit.MCG, NutrientCategory.VITAMIN, 900.0),
        Nutrient("vit_c", "Vitamin C", "Vitamina C", NutrientUnit.MG, NutrientCategory.VITAMIN, 90.0),
        Nutrient("vit_d", "Vitamin D", "Vitamina D", NutrientUnit.IU, NutrientCategory.VITAMIN, 600.0),
        Nutrient("vit_e", "Vitamin E", "Vitamina E", NutrientUnit.MG, NutrientCategory.VITAMIN, 15.0),
        Nutrient("vit_b12", "Vitamin B12", "Vitamina B12", NutrientUnit.MCG, NutrientCategory.VITAMIN, 2.4),
        Nutrient("folate", "Folate", "Acid folic", NutrientUnit.MCG, NutrientCategory.VITAMIN, 400.0),

        // --- Key minerals ---
        Nutrient("iron", "Iron", "Fier", NutrientUnit.MG, NutrientCategory.MINERAL, 8.0),
        Nutrient("calcium", "Calcium", "Calciu", NutrientUnit.MG, NutrientCategory.MINERAL, 1000.0),
        Nutrient("magnesium", "Magnesium", "Magneziu", NutrientUnit.MG, NutrientCategory.MINERAL, 420.0),
        Nutrient("zinc", "Zinc", "Zinc", NutrientUnit.MG, NutrientCategory.MINERAL, 11.0),
        Nutrient("potassium", "Potassium", "Potasiu", NutrientUnit.MG, NutrientCategory.MINERAL, 3400.0),
        Nutrient("sodium", "Sodium", "Sodiu", NutrientUnit.MG, NutrientCategory.MINERAL, 2300.0),
        Nutrient("phosphorus", "Phosphorus", "Fosfor", NutrientUnit.MG, NutrientCategory.MINERAL, 700.0),

        // --- EAA totals (skeleton — full 9 EAAs + 6 NEAAs in Plan-7) ---
        Nutrient("leucine", "Leucine", "Leucină", NutrientUnit.MG, NutrientCategory.AMINO_ACID, 2730.0),
        Nutrient("lysine", "Lysine", "Lizină", NutrientUnit.MG, NutrientCategory.AMINO_ACID, 2080.0),
        Nutrient("methionine", "Methionine", "Metionină", NutrientUnit.MG, NutrientCategory.AMINO_ACID, 1040.0),

        // --- Other (n-3, n-6, glycemic load — Plan-7 backfills ratios) ---
        Nutrient("omega_3", "Omega-3", "Omega-3", NutrientUnit.G, NutrientCategory.OTHER, 1.6),
        Nutrient("omega_6", "Omega-6", "Omega-6", NutrientUnit.G, NutrientCategory.OTHER, 17.0),
    )

    /** O(1) lookup by id. */
    private val byId: Map<String, Nutrient> = all.associateBy { it.id }

    fun byId(id: String): Nutrient? = byId[id]

    /** Returns nutrients of [category] in catalog order. */
    fun byCategory(category: NutrientCategory): List<Nutrient> = all.filter { it.category == category }
}
