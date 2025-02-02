package ru.zhadaev.testtasks.selsup;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private static final String URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private static final AtomicInteger API_CALL_COUNTER = new AtomicInteger();

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    HttpHeaders headers = new HttpHeaders();
    RestTemplate restTemplate;
    private final int requestLimit;

    /**
     * Private constructor to prevent instantiation
     *
     * @param restTemplate - the RestTemplate instance
     * @param timeUnit     - the time period in which the limit on the number of requests is set
     * @param requestLimit - the maximum number of requests in this time period.
     */
    private CrptApi(RestTemplate restTemplate, TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.headers.setContentType(MediaType.APPLICATION_JSON);
        this.restTemplate = restTemplate;
        Executors.newScheduledThreadPool(1)
                .scheduleAtFixedRate(getLimitsRefresher(), 0L, 1, timeUnit);
    }

    /**
     * Inner static helper class responsible for holding the Singleton instance
     */
    private static class SingletonHelper {
        private static CrptApi INSTANCE;

        private static void createInstance(RestTemplate restTemplate, TimeUnit timeUnit, int requestLimit) {
            if (INSTANCE == null) {
                INSTANCE = new CrptApi(restTemplate, timeUnit, requestLimit);
            }
        }
    }

    /**
     * Method to provide global access to the Singleton instance
     *
     * @return Instance of the CrptApi
     */
    public static CrptApi getInstance(RestTemplate restTemplate, TimeUnit timeUnit, int requestLimit) {
        SingletonHelper.createInstance(restTemplate, timeUnit, requestLimit);
        return SingletonHelper.INSTANCE;
    }

    /**
     * @return Runnable task that refreshes request limits.
     */
    private Runnable getLimitsRefresher() {
        return () -> {
            lock.lock();
            try {
                API_CALL_COUNTER.set(0);
                condition.signal();
                System.out.println("Request limits refreshed");
            } finally {
                lock.unlock();
            }
        };
    }

    /**
     * @implSpec The only method that needs to be implemented is the creation
     * of a document for the entry into circulation of goods produced in the Russian Federation.
     * The document and signature must be passed to the method as a Java object and string, respectively.
     */
    public Document createDocument(Document document, String signature) {
        try {
            lock.lock();
            API_CALL_COUNTER.incrementAndGet();
            awaitIfNeed();

            HttpEntity<Document> request = new HttpEntity<>(document, headers);
            ResponseEntity<Document> response = restTemplate.postForEntity(URL, request, Document.class);
            if (response.getStatusCode() == HttpStatus.CREATED) {
                return response.getBody();
            }
            throw new RuntimeException("Unexpected response status: " + response.getStatusCode());
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * @implNote Turns sleeping if requestLimit exceeded.
     * @see CrptApi#getLimitsRefresher()
     */
    private void awaitIfNeed() throws InterruptedException {
        if (API_CALL_COUNTER.get() > requestLimit) {
            String threadName = Thread.currentThread().getName();
            System.out.println("Thread " + threadName + " awaiting...");
            condition.await();
        }
    }

    /**
     * A utility class that configures and returns an instance of RestTempate
     */
    public class RestTemplateHttpsPostUtil {
        private static RestTemplate restTemplateInstance;

        private RestTemplateHttpsPostUtil() {
        }

        public static RestTemplate getRestTemplate() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            if (restTemplateInstance == null) {
                restTemplateInstance = createSecureRestTemplate();
            }

            return restTemplateInstance;
        }

        /**
         * @return RestTemplate for HTTPS protocol operation
         */
        private static RestTemplate createSecureRestTemplate() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial((chain, authType) -> true).build();

            CloseableHttpClient httpClient = HttpClients.custom()
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build();
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            return new RestTemplate(factory);
        }
    }

    @Data
    public class Document {
        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("doc_id")
        private String docId;

        @JsonProperty("doc_status")
        private String docStatus;

        @JsonProperty("doc_type")
        private String docType;

        @JsonProperty("import_request")
        private boolean importRequest;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private Date productionDate;

        @JsonProperty("production_type")
        private String productionType;

        @JsonProperty("products")
        private List<Product> products;

        @JsonProperty("reg_date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private Date regDate;

        @JsonProperty("reg_number")
        private String regNumber;

        @Data
        public static class Product {
            @JsonProperty("certificate_document")
            private String certificateDocument;

            @JsonProperty("certificate_document_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            private Date certificateDocumentDate;

            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;

            @JsonProperty("owner_inn")
            private String ownerInn;

            @JsonProperty("producer_inn")
            private String producerInn;

            @JsonProperty("production_date")
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
            private Date productionDate;

            @JsonProperty("tnved_code")
            private String tnvedCode;

            @JsonProperty("uit_code")
            private String uitCode;

            @JsonProperty("uitu_code")
            private String uituCode;
        }
    }
}
