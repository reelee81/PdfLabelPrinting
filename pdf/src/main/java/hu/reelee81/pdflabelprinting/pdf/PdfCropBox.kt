package hu.reelee81.pdflabelprinting.pdf

import com.itextpdf.kernel.geom.Rectangle
import hu.reelee81.pdflabelprinting.core.PdfCropRect

fun PdfCropRect.applyTo(box: Rectangle): Rectangle = Rectangle(
    box.x + box.width * left,
    box.y + box.height * bottom,
    box.width * (right - left),
    box.height * (top - bottom)
)
