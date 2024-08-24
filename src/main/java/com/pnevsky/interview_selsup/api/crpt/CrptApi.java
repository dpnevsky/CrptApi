package com.pnevsky.interview_selsup.api.crpt;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

public class CrptApi{

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = LoggerFactory.getLogger(CrptApi.class);
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduledExecutor;

    CrptApi(TimeUnit timeUnit, int requestLimit) throws IllegalArgumentException{
        if (requestLimit <= 0) {
            logger.error("Request limit is negative");
            throw new IllegalArgumentException();
        }
        semaphore = new Semaphore(requestLimit);
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
        scheduledExecutor.scheduleAtFixedRate(()-> semaphore.release(requestLimit),
                1, 1, timeUnit);
    }

    public void createDocument(Document document, String signature) {
        logger.info("Attempting to create document: {}", "doc_id: " + document.getDoc_id());
        try {
            semaphore.acquire();
            String documentJSON = objectMapper.writeValueAsString(document);
            String urlString = "https://ismp.crpt.ru/api/v3/lk/documents/create";
            URL url = new URL(urlString);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Signature", signature);
            connection.setDoOutput(true);
            OutputStream outputStream = connection.getOutputStream();
            byte[] input = documentJSON.getBytes(StandardCharsets.UTF_8);
            outputStream.write(input, 0, input.length);

            logger.debug("Sent request to API: {}", documentJSON);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpsURLConnection.HTTP_OK) {
                logger.error("Failed to create document: {}", "doc_id: " + document.getDoc_id() +
                        " code: " + responseCode);
            }
            else {
                logger.info("Document created successfully: {}", "doc_id: " + document.getDoc_id() +
                        " code: " + responseCode );
            }
        } catch (IOException  | InterruptedException ex) {
            logger.error("doc_id: {} {}", document.getDoc_id(), ex.getMessage());
        }
    }

    public void shutdown() {
        scheduledExecutor.shutdown();
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class Document {
    private Description description;
    private String doc_id;
    private String doc_status;
    private DocType doc_type;
    private boolean importRequest;
    private String owner_inn;
    private String participant_inn;
    private String producer_inn;
    private Date production_date;
    private String production_type;
    private List<Product> products;
    private Date reg_date;
    private String reg_number;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static
    class Description {
        private String participantInn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static
    class Product {
        private String certificate_document;
        private Date certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private Date production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }
}

@Getter
enum DocType{
    LP_INTRODUCE_GOODS
}

class MainTest{
    public static void main(String[] args) {
        Document document = new Document();
        document.setDescription(new Document.Description());
        document.getProducts().add(new Document.Product());
        String signature = "Signature";
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 10);
        for (int i = 0; i < 100; i++) {
            crptApi.createDocument(document, signature);
        }
        crptApi.shutdown();
    }
}