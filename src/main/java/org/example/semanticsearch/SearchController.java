package org.example.semanticsearch;

import org.example.semanticsearch.service.DocumentService;
import org.example.semanticsearch.service.PdfIngestionService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class SearchController {

    private final DocumentService documentService;
    private final PdfIngestionService pdfIngestionService;

    public SearchController(DocumentService documentService, PdfIngestionService pdfIngestionService) {
        this.documentService = documentService;
        this.pdfIngestionService = pdfIngestionService;
    }

    @PostMapping("/add")
    public String addDocument(@RequestBody Map<String, String> body) {
        try {
            documentService.addDocument(body.get("titlu"), body.get("continut"));
            return "Document adaugat cu succes!";
        } catch (Exception e) {
            return "Eroare: " + e.getMessage();
        }
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String query,
                                            @RequestParam(defaultValue = "5") int topK) {
        try {
            return documentService.search(query, topK);
        } catch (Exception e) {
            return List.of(Map.of("eroare", e.getMessage()));
        }
    }

    @PostMapping("/upload-pdf")
    public String uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            String titlu = file.getOriginalFilename().replace(".pdf", "");
            int chunks = pdfIngestionService.ingestPdf(titlu, file.getInputStream());
            return "PDF ingestionat cu succes! " + chunks + " pagini adaugate.";
        } catch (Exception e) {
            return "Eroare: " + e.getMessage();
        }
    }
}