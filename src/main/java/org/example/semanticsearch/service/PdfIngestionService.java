package org.example.semanticsearch.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.semanticsearch.service.DocumentService;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfIngestionService {

    private final DocumentService documentService;

    public PdfIngestionService(DocumentService documentService) {
        this.documentService = documentService;
    }

    public int ingestPdf(String titlu, InputStream pdfStream) throws Exception {
        PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfStream.readAllBytes());
        PDFTextStripper stripper = new PDFTextStripper();

        int totalPages = document.getNumberOfPages();
        int chunksAdded = 0;

        // Impartim PDF-ul in chunk-uri de cate o pagina
        for (int page = 1; page <= totalPages; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String pageText = stripper.getText(document).trim();

            if (pageText.length() > 50) { // ignoram paginile goale
                String chunkTitlu = titlu + " - pagina " + page;
                documentService.addDocument(chunkTitlu, pageText);
                chunksAdded++;
            }
        }

        document.close();
        return chunksAdded;
    }
}