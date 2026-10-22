package io.github.filbeq.fuelup.core

/** Per-file tally of dropped rows and notable-but-kept rows, by reason. */
class DropReport(val name: String) {
    var rowsRead = 0
    val dropped = LinkedHashMap<String, Int>()
    val notes = LinkedHashMap<String, Int>()
    private val samples = HashMap<String, MutableList<String>>()

    fun drop(reason: String, ident: Any) {
        dropped.merge(reason, 1, Int::plus)
        sample(reason, ident)
    }

    /** Record something unusual about a row that was kept. */
    fun note(reason: String, ident: Any) {
        notes.merge(reason, 1, Int::plus)
        sample(reason, ident)
    }

    val droppedTotal: Int get() = dropped.values.sum()

    fun samples(reason: String): List<String> = samples[reason].orEmpty()

    private fun sample(reason: String, ident: Any) {
        val list = samples.getOrPut(reason) { mutableListOf() }
        if (list.size < SAMPLE_SIZE) list.add(ident.toString())
    }

    /** The report as log lines, most frequent reasons first. */
    fun lines(): List<String> = buildList {
        add(if (rowsRead > 0) "$name: $rowsRead rows read, $droppedTotal dropped" else "$name: $droppedTotal dropped")
        for ((reason, count) in dropped.entries.sortedByDescending { it.value }) {
            add("  dropped %6d  %s  e.g. %s".format(count, reason, samples(reason).joinToString(", ")))
        }
        for ((reason, count) in notes.entries.sortedByDescending { it.value }) {
            add("  noted   %6d  %s  e.g. %s".format(count, reason, samples(reason).joinToString(", ")))
        }
    }

    companion object {
        const val SAMPLE_SIZE = 5
    }
}
