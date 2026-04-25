package org.example.semanticsearch;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class DocumentService {

    private final DataSource dataSource;
    private final EmbeddingService embeddingService;

    public DocumentService(DataSource dataSource, EmbeddingService embeddingService) {
        this.dataSource = dataSource;
        this.embeddingService = embeddingService;
    }

    public void addDocument(String titlu, String continut) throws Exception {
        float[] embedding = embeddingService.embed(continut);

        String vectorStr = Arrays.toString(embedding);

        String sql = "INSERT INTO documents (titlu, continut, embedding) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, titlu);
            ps.setString(2, continut);
            ps.setObject(3, vectorStr);
            ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> search(String query, int topK) throws Exception {
        float[] queryEmbedding = embeddingService.embed(query);
        String vectorStr = Arrays.toString(queryEmbedding);

        String sql = """
                SELECT titlu, continut,
                       VECTOR_DISTANCE(embedding, ?, COSINE) as score
                FROM documents
                ORDER BY score ASC
                FETCH FIRST ? ROWS ONLY
                """;

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, vectorStr);
            ps.setInt(2, topK);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("titlu", rs.getString("titlu"));
                row.put("continut", rs.getString("continut"));
                row.put("score", rs.getDouble("score"));
                results.add(row);
            }
        }
        return results;
    }
}