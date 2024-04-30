package me.korolz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.currentTimeMillis;

public class CrptApi {

    private final String apiPostUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final long timeWindow;
    private final int requestLimit;
    private final ReentrantLock lock = new ReentrantLock();
    private static int requestCounter = 0;
    private long first = 0;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeWindow = timeUnit.toChronoUnit().getDuration().toMillis(); // Преобразование TimeUnit в миллисекундное окно
        this.requestLimit = requestLimit;
    }

    public void postDocument(Object document, String signature) throws IOException, InterruptedException {

        // Сериализация документа
        ObjectMapper objectMapper = new ObjectMapper();
        /*
        Предполагается, что документ составлен верно и не требует доработок перед сериализацией
        Также предполагается, что документ имеет все поля, предоставленные в JSON'е этого документа из тестового задания
         */
        String documentJson = objectMapper.writeValueAsString(document);

        // Создание тела запроса с документом и подписью
        String requestBody = String.format("{\"document\":\"%s\",\"signature\":\"%s\"}", documentJson, signature);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiPostUrl))
                .setHeader("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient client = HttpClient.newHttpClient();

        // Проверка на допустимость отправки запроса
        lock.lock();
        try {
            if (requestCounter == requestLimit) {
                long last = currentTimeMillis();
                long res = last - first;
                // Ждем следующее окно чтобы отправить запрос
                Thread.sleep(res > timeWindow ? 0 : timeWindow - res);
                requestCounter = 0;
            }
            if (requestCounter == 0) {
                first = currentTimeMillis();
            }
            requestCounter++;

            // Отправка запроса
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Документ зарегистрирован: " + response.body());
            } else {
                System.out.println("Ошибка при регистрации документа: " + response.statusCode());
            }
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        TimeUnit timeUnit = TimeUnit.SECONDS;
        int requestLimit = 3;

        CrptApi crptApi = new CrptApi(timeUnit, requestLimit);

        class Document {
            String doc_id;
            public Document(String doc_id) {
                this.doc_id = doc_id;
            }
            public String getDoc_id() {
                return doc_id;
            }
            public void setDoc_id(String doc_id) {
                this.doc_id = doc_id;
            }
        }
        Document document = new Document("dummyDocument");
        String signature = "dummySignature";

        // Запуск метода
        crptApi.postDocument(document, signature);
    }
}