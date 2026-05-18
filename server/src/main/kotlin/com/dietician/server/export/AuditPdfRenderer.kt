package com.dietician.server.export

import com.dietician.server.repo.AuditRow
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * AI Act Art 12 audit-trail PDF renderer. Plain unstyled grid:
 *
 *   header line 1: "Dietician audit-log export"
 *   header line 2: "Subject: <uuid> · Window: <from> -> <to> · Rows: N"
 *   one row per audit entry (occurred_at, kind, model, costCents)
 *
 * Pure data dump — NO marketing copy, NO emoji, NO emotion-inferring
 * column. Per session rule (Art 5(1)(f)) we never claim to read mood
 * from a missing meal_event; the export is the EXPLICIT trail only.
 */
object AuditPdfRenderer {
    fun render(subjectId: UUID, rows: List<AuditRow>, from: Instant, to: Instant): ByteArray {
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        PDDocument().use { doc ->
            var page = PDPage()
            doc.addPage(page)
            var cs = PDPageContentStream(doc, page)
            var y = 760f

            fun newline(size: Float = 10f) {
                y -= (size + 2f)
                if (y < 50f) {
                    cs.close()
                    page = PDPage()
                    doc.addPage(page)
                    cs = PDPageContentStream(doc, page)
                    y = 760f
                }
            }

            fun draw(text: String, sz: Float = 10f, bold: Boolean = false) {
                cs.beginText()
                cs.setFont(if (bold) fontBold else font, sz)
                cs.newLineAtOffset(50f, y)
                cs.showText(text.replace("\n", " ").take(200))
                cs.endText()
                newline(sz)
            }

            draw("Dietician audit-log export", sz = 14f, bold = true)
            draw("Subject: $subjectId")
            draw("Window: $from  ->  $to")
            draw("Rows: ${rows.size}")
            draw("Generated: ${LocalDate.now()}")
            newline(10f)
            draw("occurred_at  kind  model  cost_cents", bold = true)
            newline(2f)
            for (row in rows) {
                val line = "${row.occurredAt}  ${row.kind}  ${row.model ?: "-"}  ${row.costCents ?: "-"}"
                draw(line)
            }
            cs.close()

            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }
}
