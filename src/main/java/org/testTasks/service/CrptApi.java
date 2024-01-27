package org.testTasks.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.testTasks.entity.ProductDocument;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClientBuilder.create().build();
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static final List<Long> scheduleTimeOfSentDocuments
            = new ArrayList<>(); // Список будет хранить время отправки последних документов
    private static final HttpPost request = new HttpPost("https://ismp.crpt.ru/api/v3/lk/documents/create");
    private final long timeLimitMillis; // Промежуток времени
    private final int requestLimit; // Максимальное кол-во запросов в промежуток времени


    public CrptApi(int requestLimit, long timeLimit, TimeUnit timeUnit) {
        this.timeLimitMillis = TimeUnit.MILLISECONDS.convert(timeLimit, timeUnit);
        this.requestLimit = requestLimit;
    }


    public synchronized void createDocument(ProductDocument productDocument, String signature) {

        if (scheduleTimeOfSentDocuments.size() < requestLimit) {

            scheduleTimeOfSentDocuments.add(scheduleTimeOfSentDocuments.size(), System.currentTimeMillis());
            System.out.println(scheduleTimeOfSentDocuments);

            executorService.execute(() -> sendDocumentAsJsonByHttp(productDocument, signature));
        } else {

            System.out.println(scheduleTimeOfSentDocuments);

            // Планируем отложенную задачу через определённое время
            executorService.schedule(() -> sendDocumentAsJsonByHttp(productDocument, signature),
                    (scheduleTimeOfSentDocuments.get(0) - System.currentTimeMillis()) + timeLimitMillis,
                    TimeUnit.MILLISECONDS);

            scheduleTimeOfSentDocuments.add(
                    scheduleTimeOfSentDocuments.size(), scheduleTimeOfSentDocuments.get(0) + timeLimitMillis);
            scheduleTimeOfSentDocuments.remove(0);

        }
    }

    private static void sendDocumentAsJsonByHttp(ProductDocument productDocument, String signature) {

        // Добавляем подпись в документ
        enrichProductDocumentWithSignature(productDocument, signature);

        request.addHeader("Content-Type", "application/json");

        StringEntity params = null;

        try {
            params = new StringEntity(convertDocumentToJson(productDocument));
        } catch (UnsupportedEncodingException e) {
            System.err.println("UnsupportedEncodingException in .sendDocumentAsJsonByHttp() method");
        }

        request.setEntity(params);

        try {
            httpClient.execute(request);
        } catch (ClientProtocolException e) {
            System.err.println("ClientProtocolException while sending JSON in .sendDocumentAsJsonByHttp() method");
        } catch (IOException e) {
            System.err.println("IOException while sending JSON in .sendDocumentAsJsonByHttp() method");
        }

    }

    private static void enrichProductDocumentWithSignature(ProductDocument productDocument, String signature) {
        productDocument.setSignature(signature);
    }

    private static String convertDocumentToJson(ProductDocument document) {

        String json = null;
        try {
            json = objectMapper.writeValueAsString(document);

        } catch (JsonProcessingException e) {
            System.err.println("Error occurred while converting ProductDocument to JSON");
        }

        return json;
    }

}
