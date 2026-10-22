package hu.reelee81.pdflabelprinting.pdf

interface PdfProgress {
    fun advance(units: Int)
    fun idle()
}
