package eu.kanade.tachiyomi.ui.reader2

import eu.kanade.tachiyomi.data.database.models.Chapter

class ChapterLoadBySource {
    fun get(allChapters: List<Chapter>): List<Chapter> {
        return allChapters.sortedByDescending { it.source_order }
    }
}

class ChapterLoadByNumber {
    fun get(allChapters: List<Chapter>, selectedChapter: Chapter): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val chaptersByNumber = allChapters.groupBy { it.chapter_number }

        for ((number, chaptersForNumber) in chaptersByNumber) {
            val preferredChapter = when {
                // Make sure the selected chapter is always present
                number == selectedChapter.chapter_number -> selectedChapter
                // If there is only one chapter for this number, use it
                chaptersForNumber.size == 1 -> chaptersForNumber.first()
                // Prefer a chapter of the same scanlator as the selected
                else -> chaptersForNumber.find { it.scanlator == selectedChapter.scanlator }
                        ?: chaptersForNumber.first()
            }
            chapters.add(preferredChapter)
        }
        return chapters.sortedBy { it.chapter_number }
    }
}
