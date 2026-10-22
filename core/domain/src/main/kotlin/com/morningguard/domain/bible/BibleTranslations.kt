package com.morningguard.domain.bible

enum class BibleTranslationSource {
    OPEN_BIBLE,
    API_BIBLE,
}

data class BibleTranslationOption(
    val id: String,
    /** Short label for chips, e.g. KJV, WEB */
    val chipLabel: String,
    val fullName: String,
    val language: String = "English",
    val source: BibleTranslationSource = BibleTranslationSource.OPEN_BIBLE,
    /** Used to match your licensed Bible on API.Bible (e.g. NIV, NKJV, NLT). */
    val apiBibleAbbreviation: String? = null,
) {
    val usesApiBible: Boolean get() = source == BibleTranslationSource.API_BIBLE
}

object BibleTranslations {
    val supported: List<BibleTranslationOption> = listOf(
        BibleTranslationOption(
            id = "niv",
            chipLabel = "NIV",
            fullName = "New International Version",
            source = BibleTranslationSource.API_BIBLE,
            apiBibleAbbreviation = "NIV",
        ),
        BibleTranslationOption(
            id = "nkjv",
            chipLabel = "NKJV",
            fullName = "New King James Version",
            source = BibleTranslationSource.API_BIBLE,
            apiBibleAbbreviation = "NKJV",
        ),
        BibleTranslationOption(
            id = "nlt",
            chipLabel = "NLT",
            fullName = "New Living Translation",
            source = BibleTranslationSource.API_BIBLE,
            apiBibleAbbreviation = "NLT",
        ),
        BibleTranslationOption("web", "WEB", "World English Bible"),
        BibleTranslationOption("kjv", "KJV", "King James Version"),
        BibleTranslationOption("asv", "ASV", "American Standard Version (1901)"),
        BibleTranslationOption("bbe", "BBE", "Bible in Basic English"),
        BibleTranslationOption("darby", "DARBY", "Darby Bible"),
        BibleTranslationOption("dra", "DRA", "Douay-Rheims 1899"),
        BibleTranslationOption("ylt", "YLT", "Young's Literal Translation"),
        BibleTranslationOption("oeb-us", "OEB-US", "Open English Bible (US)"),
        BibleTranslationOption("oeb-cw", "OEB-CW", "Open English Bible (Commonwealth)"),
        BibleTranslationOption("webbe", "WEB-BE", "World English Bible (British)"),
        BibleTranslationOption("clementine", "VULG", "Clementine Latin Vulgate", language = "Latin"),
        BibleTranslationOption("cuv", "CUV", "Chinese Union Version", language = "Chinese"),
        BibleTranslationOption("bkr", "BKR", "Bible kralická", language = "Czech"),
        BibleTranslationOption("almeida", "ALMEIDA", "João Ferreira de Almeida", language = "Portuguese"),
        BibleTranslationOption("rccv", "RCCV", "Protestant Romanian Cornilescu", language = "Romanian"),
        BibleTranslationOption("cherokee", "CHEROKEE", "Cherokee New Testament", language = "Cherokee"),
    )

    val defaultId: String = "niv"

    fun find(id: String): BibleTranslationOption? =
        supported.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun normalizeId(id: String): String = find(id)?.id ?: defaultId

    fun chipLabel(id: String): String = find(id)?.chipLabel ?: id.uppercase()

    fun openBibleOptions(): List<BibleTranslationOption> =
        supported.filter { it.source == BibleTranslationSource.OPEN_BIBLE }

    fun apiBibleOptions(): List<BibleTranslationOption> =
        supported.filter { it.source == BibleTranslationSource.API_BIBLE }
}
