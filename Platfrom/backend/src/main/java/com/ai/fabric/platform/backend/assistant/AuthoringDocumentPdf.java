package com.ai.fabric.platform.backend.assistant;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** PDF text extraction only. No rendering, OCR, embedded attachments, actions or URL handling. */
final class AuthoringDocumentPdf {
    private AuthoringDocumentPdf() { }

    static String extract(byte[] bytes, List<String> warnings) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) {
                throw new PdfFailure("Encrypted or extraction-restricted PDFs are unsupported; upload sanitized text.");
            }
            int pages = document.getNumberOfPages();
            int limit = Math.min(pages, AuthoringDocumentImporter.MAX_PDF_PAGES);
            BoundedWriter output = new BoundedWriter();
            PDFTextStripper stripper = new PDFTextStripper() {
                private int glyphs;

                @Override
                protected void processTextPosition(TextPosition text) {
                    // PDFTextStripper buffers positions until page end; bound that buffer as well.
                    if (++glyphs > AuthoringDocumentImporter.MAX_CONTENT_CHARS) {
                        throw new ExtractionLimit();
                    }
                    super.processTextPosition(text);
                }
            };
            boolean stopped = false;
            for (int page = 1; page <= limit; page++) {
                try {
                    output.write("[page:" + page + "]\n");
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    stripper.writeText(document, output);
                } catch (ExtractionLimit ex) {
                    warnings.add("PDF extraction truncated on page " + page + ": 200000-character/glyph extraction limit reached; remaining page text and later pages were not extracted (unknown omitted character count).");
                    stopped = true;
                    break;
                }
            }
            if (pages > limit) {
                warnings.add("PDF pages truncated: selected pages 1-" + limit + " of " + pages + "; omitted "
                        + (pages - limit) + " page(s) at the 50-page limit.");
            }
            String content = output.text.toString();
            if (content.replaceAll("\\[page:[0-9]+]", "").isBlank()) {
                if (stopped) {
                    throw new PdfFailure("PDF extraction limit reached before text could be retained; upload a smaller sanitized text document.");
                }
                throw new PdfFailure("PDF has no extractable text in selected pages; image-only/scanned PDF OCR is unsupported. Upload text or an API specification.");
            }
            warnings.add("PDF text only; image/OCR content and embedded files were not extracted. No formal API operations inferred.");
            return content;
        } catch (PdfFailure ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new PdfFailure("Malformed, encrypted or unreadable PDF; upload sanitized text or an API specification.");
        }
    }

    private static final class BoundedWriter extends Writer {
        private final StringBuilder text = new StringBuilder();

        @Override
        public void write(char[] chars, int offset, int length) {
            int available = AuthoringDocumentImporter.MAX_CONTENT_CHARS - text.length();
            text.append(chars, offset, Math.min(available, length));
            if (length > available) {
                if (!text.isEmpty() && Character.isHighSurrogate(text.charAt(text.length() - 1))) {
                    text.setLength(text.length() - 1);
                }
                throw new ExtractionLimit();
            }
        }

        @Override
        public void flush() { }

        @Override
        public void close() { }
    }

    private static final class ExtractionLimit extends RuntimeException { }

    private static final class PdfFailure extends IllegalArgumentException {
        private PdfFailure(String message) {
            super(message);
        }
    }
}
