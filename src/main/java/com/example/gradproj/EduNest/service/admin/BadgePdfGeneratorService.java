package com.example.gradproj.EduNest.service.admin;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.BorderRadius;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class BadgePdfGeneratorService {

    private static final Color BLUE         = new DeviceRgb(37, 99, 235);
    private static final Color BLUE_DARK    = new DeviceRgb(30, 58, 138);
    private static final Color NAVY         = new DeviceRgb(17, 24, 39);
    private static final Color DARK_GRAY    = new DeviceRgb(55, 65, 81);
    private static final Color MED_GRAY     = new DeviceRgb(107, 114, 128);
    private static final Color LIGHT_GRAY   = new DeviceRgb(156, 163, 175);
    private static final Color BG_BLUE      = new DeviceRgb(248, 250, 255);
    private static final Color BORDER_BLUE  = new DeviceRgb(224, 234, 255);
    private static final Color SLATE        = new DeviceRgb(100, 116, 139);   // desc box text
    private static final Color SLATE_BG     = new DeviceRgb(241, 245, 249);   // desc box bg
    private static final Color SLATE_BORDER = new DeviceRgb(203, 213, 225);   // desc box border
    private static final Color INDIGO       = new DeviceRgb(99, 102, 241);    // note accent
    private static final Color INDIGO_BG    = new DeviceRgb(238, 242, 255);   // note bg
    private static final Color INDIGO_BORDER= new DeviceRgb(199, 210, 254);   // note border
    private static final Color WHITE        = new DeviceRgb(255, 255, 255);

    public ByteArrayOutputStream generateBadgeCertificate(
            String userFullName,
            String badgeName,
            String badgeType,
            String badgeDescription,
            String recognitionNote) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outputStream));
            Document doc = new Document(pdfDoc, PageSize.A4);
            doc.setMargins(0, 0, 48, 0);

            // ── Full-width top bar on canvas ──
            pdfDoc.addEventHandler(PdfDocumentEvent.START_PAGE, event -> {
                PdfDocumentEvent e = (PdfDocumentEvent) event;
                PdfCanvas canvas = new PdfCanvas(e.getPage());
                float w = e.getPage().getPageSize().getWidth();
                float h = e.getPage().getPageSize().getHeight();

                // navy bar (4px)
                canvas.setFillColor(NAVY)
                        .rectangle(0, h - 4, w, 4)
                        .fill();

                // blue bar (3px) below navy
                canvas.setFillColor(BLUE)
                        .rectangle(0, h - 7, w, 3)
                        .fill();

                canvas.release();
            });

            // ── Content ──
            Div content = new Div()
                    .setMarginTop(44)
                    .setMarginLeft(64)
                    .setMarginRight(64);

            // Brand + separator
            content.add(new Paragraph("EduNest")
                    .setFontSize(22)
                    .setBold()
                    .setFontColor(NAVY)
                    .setMarginBottom(6));

            content.add(new Div()
                    .setHeight(1)
                    .setBackgroundColor(BORDER_BLUE)
                    .setMarginBottom(36));

            // Greeting
            content.add(new Paragraph("Dear " + userFullName + ",")
                    .setFontSize(11)
                    .setFontColor(MED_GRAY)
                    .setMarginBottom(8));

            // Title
            content.add(new Paragraph("You've been awarded a new badge.")
                    .setFontSize(22)
                    .setBold()
                    .setFontColor(NAVY)
                    .setMarginBottom(20));

            // ── Congratulations paragraph ──
            content.add(new Paragraph(
                    "Congratulations on this well-deserved achievement. " +
                            "This badge is a reflection of the dedication, effort, and excellence " +
                            "you have consistently demonstrated throughout your learning journey on EduNest. " +
                            "We are proud to recognise your hard work and encourage you to keep pushing forward.")
                    .setFontSize(11)
                    .setFontColor(DARK_GRAY)
                    .setMultipliedLeading(1.6f)
                    .setMarginBottom(32));

            // ── Badge box ──
            Div badgeBox = new Div()
                    .setBackgroundColor(BG_BLUE)
                    .setBorder(new SolidBorder(BORDER_BLUE, 1))
                    .setBorderRadius(new BorderRadius(10))
                    .setPaddingTop(40)
                    .setPaddingBottom(40)
                    .setPaddingLeft(24)
                    .setPaddingRight(24)
                    .setMarginBottom(28);

            badgeBox.add(new Paragraph(badgeType.toUpperCase().replace("_", " "))
                    .setFontSize(9)
                    .setBold()
                    .setFontColor(BLUE)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setCharacterSpacing(2f)
                    .setMarginBottom(12));

            badgeBox.add(new Paragraph(badgeName)
                    .setFontSize(26)
                    .setBold()
                    .setFontColor(NAVY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(0));

            content.add(badgeBox);

            // ── Description — slate style ──
            if (badgeDescription != null && !badgeDescription.isBlank()) {
                Div descBox = new Div()
                        .setBackgroundColor(SLATE_BG)
                        .setBorderLeft(new SolidBorder(SLATE_BORDER, 3))
                        .setBorderRadius(new BorderRadius(8))
                        .setPadding(16)
                        .setMarginBottom(16);

                descBox.add(new Paragraph("Badge Description")
                        .setFontSize(9)
                        .setBold()
                        .setFontColor(SLATE)
                        .setCharacterSpacing(0.8f)
                        .setMarginBottom(6));

                descBox.add(new Paragraph(badgeDescription)
                        .setFontSize(10.5f)
                        .setFontColor(DARK_GRAY)
                        .setMargin(0));

                content.add(descBox);
            }

            // ── Recognition note — indigo style ──
            if (recognitionNote != null && !recognitionNote.isBlank()) {
                Div noteBox = new Div()
                        .setBackgroundColor(INDIGO_BG)
                        .setBorderLeft(new SolidBorder(INDIGO, 3))
                        .setBorderRadius(new BorderRadius(8))
                        .setPadding(16)
                        .setMarginBottom(16);

                noteBox.add(new Paragraph("Recognition Note")
                        .setFontSize(9)
                        .setBold()
                        .setFontColor(INDIGO)
                        .setCharacterSpacing(0.8f)
                        .setMarginBottom(6));

                noteBox.add(new Paragraph(recognitionNote)
                        .setFontSize(10.5f)
                        .setFontColor(DARK_GRAY)
                        .setMargin(0));

                content.add(noteBox);
            }

            // ── Footer ──
            content.add(new Div()
                    .setHeight(1)
                    .setBackgroundColor(BORDER_BLUE)
                    .setMarginTop(24)
                    .setMarginBottom(16));

            content.add(new Paragraph(
                    "Awarded on " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
                            + "  ·  The EduNest Team")
                    .setFontSize(9)
                    .setFontColor(LIGHT_GRAY)
                    .setMargin(0));

            doc.add(content);
            doc.close();

        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF certificate", e);
        }

        return outputStream;
    }
}