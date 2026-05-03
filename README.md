# Proiect: Căutare Semantică cu Oracle AI Vector Search și Spring Boot

Acest repository conține implementarea unui sistem de căutare semantică (Retrieval-Augmented Generation - RAG context) bazat pe inteligență artificială, utilizând **Oracle 23ai**, **Spring Boot**, **ONNX Runtime** și **Docker**.

Sistemul permite încărcarea de documente PDF, procesarea și transformarea textului în vectori (embeddings), și interogarea acestora folosind limbaj natural pentru a găsi cele mai relevante informații pe baza contextului semantic, nu doar a cuvintelor cheie.

---

## 1. Arhitectura Soluției

Arhitectura soluției urmează un model Client-Server și este structurată astfel:

* **Frontend / Client (Postman / Swagger UI):** Interfața prin care utilizatorul interacționează cu sistemul (upload documente, trimitere interogări text).
* **Backend (Java Spring Boot):**
  * **Document Parser (`Apache PDFBox`):** Extrage textul din fișierele PDF încărcate și îl împarte în segmente (chunks) pentru o procesare eficientă.
  * **Embedding Service (`ONNX Runtime`):** Rulează local un model AI pre-antrenat (ex. *HuggingFace All-MiniLM-L6-v2*) pentru a transforma segmentele de text și întrebările utilizatorului în vectori denși cu 384 de dimensiuni.
* **Database Layer (Oracle Database 23ai):** Stochează textul original sub formă de `CLOB` și reprezentarea sa matematică sub formă de `VECTOR`. Motorul Oracle AI Vector Search este folosit pentru a calcula distanța dintre vectori la nivel de bază de date.

---

## 2. Modelul de Date

Baza de date conține o tabelă principală pentru stocarea documentelor și a vectorilor asociați. Tabela a fost creată direct în instanța Oracle 23ai:

```sql
-- Crearea tabelei principale
CREATE TABLE documents (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY, 
    titlu VARCHAR2(500), 
    continut CLOB, 
    embedding VECTOR(384, FLOAT32)
);
```

Pentru a optimiza timpul de răspuns la volume mari de date, am implementat un index de tip graf (HNSW) cu metrica de distanță COSINE:
```sql
-- Crearea indexului vectorial
CREATE VECTOR INDEX idx_doc_embedding ON documents(embedding) 
ORGANIZATION INMEMORY NEIGHBOR GRAPH 
DISTANCE COSINE WITH TARGET ACCURACY 95;
```

## 3. Configurații Software și Hardware
Sistemul a fost dezvoltat și testat pe un mediu localizat, utilizând containerizare pentru baza de date.

Hardware:
* **Procesor:** CPU multi-core (x86_64), suport AVX necesar pentru procesarea eficientă a modelelor ONNX.
* **Memorie RAM:** Minim 8 GB (16 GB recomandați pentru alocarea vector_memory_size în Oracle și rularea JVM-ului).
* **Stocare:** SSD pentru performanță la citirea/scrierea bazei de date.

Software & Versiuni:
* **Sistem de Operare:** Windows 11 / Linux (Ubuntu) / macOS
* **Docker Engine:** v24.x (înlocuiește cu versiunea ta: docker -v)
* **Java Development Kit (JDK):** Java 21 (înlocuiește cu versiunea ta: java -version)
* **Apache Maven:** v3.9.x (înlocuiește cu versiunea ta: mvn -v)
* **Oracle Database:** Imaginea Docker gvenzl/oracle-free:23-slim (Oracle 23ai)
* **Spring Boot:** v3.2.x
* **IDE:** IntelliJ IDEA Ultimate/Community

## 4. Fragmente de Cod Relevante
### 4.1. Configurare Bază de Date (Docker)
Inițializarea instanței Oracle 23ai a fost realizată prin Docker, mapând portul 1522 pentru a evita conflictele cu alte instalări Oracle existente:

docker rm -f oracle23ai
docker run -d --name oracle23ai -p 1522:1521 -e ORACLE_PASSWORD=Parola123 gvenzl/oracle-free:23-slim

Configurarea utilizatorului și a memoriei alocate procesării vectoriale:

docker exec -it oracle23ai sqlplus system/Parola123@FREE

```sql
CREATE USER vecuser IDENTIFIED BY Parola123;
GRANT ALL PRIVILEGES TO vecuser;
ALTER SYSTEM SET vector_memory_size=512M SCOPE=SPFILE;
```

-- Necesită restart container pentru aplicarea vector_memory_size: docker restart oracle23ai

### 4.2. Execuția Căutării Semantice (Java/SQL)
Logica de potrivire a contextului are loc la nivelul bazei de date, folosind funcția nativă VECTOR_DISTANCE:
// Exemplu concept de interogare folosind JDBC/Spring Data
String sqlQuery = """
    SELECT id, titlu, continut, 
           VECTOR_DISTANCE(embedding, ?, COSINE) as similarity_score
    FROM documents
    ORDER BY similarity_score ASC
    FETCH FIRST 5 ROWS ONLY
""";
// '?' reprezintă vectorul generat în Java din textul căutat de utilizator

## 5. Capturi de Ecran și Execuție

### 5.1. Pornirea Infrastructurii Docker
![Rulare Docker Oracle](Căutare_Semantică.png): Containerul Oracle 23ai rulând cu succes și expunând portul 1522.

### 5.2. Testarea API-ului - Ingestie PDF
![Upload PDF](Docker.png): Endpoint-ul POST /api/documents/upload, care extrage și vectorizează textul din PDF.

### 5.3. Testarea API-ului - Căutare Semantică
![Rezultate Search](Ingestie_PDF.png): Răspunsul primit în urma unei interogări în limbaj natural. Sistemul returnează cele mai relevante paragrafe.

## 6. Interpretarea Rezultatelor
În urma testării sistemului pe diverse seturi de documente, am observat următoarele:

Eficiența Căutării Semantice: Spre deosebire de o interogare clasică (de tipul LIKE '%cuvânt%'), care caută o potrivire exactă a caracterelor, soluția de față utilizează Distanța Cosinus (Cosine Distance). Sistemul returnează rezultate valide chiar dacă se folosesc sinonime sau formulări diferite ale aceleiași idei.

Scorurile de Similaritate: Valoarea returnată de VECTOR_DISTANCE cu metrica COSINE variază teoretic între 0 și 2. Un scor foarte apropiat de 0.0 indică o similitudine semantică ridicată (vectorii indică spre aceeași "direcție" în spațiul conceptual), în timp ce scorurile mai mari indică lipsa relevanței.

Performanța Indexării HNSW: Adăugarea indexului NEIGHBOR GRAPH a transformat o căutare liniară exactă (lentă la volume mari) într-o căutare aproximativă (ANN). Cu TARGET ACCURACY 95, latența interogării este sub-secundară, fără o degradare vizibilă a calității răspunsurilor.

## 7. Referințe Bibliografice
1. Oracle Corporation. (2024). Oracle Database 23ai: AI Vector Search Documentation. Preluat de pe: https://docs.oracle.com/en/database/oracle/oracle-database/26/
2. Spring.io. (2024). Spring Boot Reference Guide. Preluat de pe: https://docs.spring.io/spring-boot/index.html
3. ONNX Runtime Developers. (2024). ONNX Runtime Documentation - Java API. Preluat de pe: https://onnxruntime.ai/docs/get-started/with-java.html
4. Apache Software Foundation. Apache PDFBox. Preluat de pe: https://pdfbox.apache.org/
