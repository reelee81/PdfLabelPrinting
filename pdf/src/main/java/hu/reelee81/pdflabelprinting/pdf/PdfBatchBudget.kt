package hu.reelee81.pdflabelprinting.pdf

object PdfBatchBudget {
    fun bytes(): Long = Runtime.getRuntime().maxMemory() / 8
}
