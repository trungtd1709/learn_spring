package com.example.learn_spring_boot.api.controller;

import com.example.learn_spring_boot.core.repository.kafka.KafkaProducerAdvanced;
import com.example.learn_spring_boot.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Controller để test các Kafka Producer patterns
 * 
 * Các endpoints để demo:
 * 1. Fire-and-forget
 * 2. Synchronous send
 * 3. Asynchronous send
 * 4. Batch send
 * 5. Send với headers
 * 6. Send với CompletableFuture
 */
@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
@Slf4j
public class KafkaController {

    private final KafkaProducerAdvanced kafkaProducer;

    // ============================================================================
    // FIRE AND FORGET
    // ============================================================================
    
    /**
     * Gửi message theo kiểu fire-and-forget
     * Không đợi kết quả, throughput cao nhất
     */
    @PostMapping("/fire-and-forget")
    public ResponseEntity<ApiResponse<String>> sendFireAndForget(
            @RequestParam String topic,
            @RequestBody String message) {
        
        kafkaProducer.sendFireAndForget(topic, message);
        
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .code(200)
                .message("Message sent (fire-and-forget)")
                .data("Message submitted to topic: " + topic)
                .build());
    }

    // ============================================================================
    // SYNCHRONOUS SEND
    // ============================================================================
    
    /**
     * Gửi message đồng bộ và đợi kết quả
     * Đảm bảo biết được message đã được gửi thành công
     */
    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Object>> sendSync(
            @RequestParam String topic,
            @RequestParam(required = false) String key,
            @RequestBody String message) {
        
        try {
            var metadata = kafkaProducer.sendSync(topic, key, message);
            
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(200)
                    .message("Message sent successfully")
                    .data(new SendResult(
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset(),
                            metadata.timestamp()
                    ))
                    .build());
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.builder()
                    .code(500)
                    .message("Failed to send message: " + e.getMessage())
                    .build());
        }
    }

    // ============================================================================
    // ASYNCHRONOUS SEND
    // ============================================================================
    
    /**
     * Gửi message bất đồng bộ với callback
     * Response trả về ngay, không đợi Kafka xác nhận
     */
    @PostMapping("/async")
    public ResponseEntity<ApiResponse<String>> sendAsync(
            @RequestParam String topic,
            @RequestParam(required = false) String key,
            @RequestBody String message) {
        
        kafkaProducer.sendAsync(topic, key, message);
        
        return ResponseEntity.accepted().body(ApiResponse.<String>builder()
                .code(202)
                .message("Message submitted for async send")
                .data("Check logs for result")
                .build());
    }

    // ============================================================================
    // COMPLETABLE FUTURE
    // ============================================================================
    
    /**
     * Gửi message với CompletableFuture
     * Đợi kết quả và trả về response
     */
    @PostMapping("/future")
    public CompletableFuture<ResponseEntity<ApiResponse<Object>>> sendWithFuture(
            @RequestParam String topic,
            @RequestParam(required = false) String key,
            @RequestBody String message) {
        
        return kafkaProducer.sendAndGetResponse(topic, key, message)
                .thenApply(response -> {
                    if (response.success()) {
                        return ResponseEntity.ok(ApiResponse.builder()
                                .code(200)
                                .message("Message sent successfully")
                                .data(response)
                                .build());
                    } else {
                        return ResponseEntity.internalServerError().body(ApiResponse.builder()
                                .code(500)
                                .message("Failed to send: " + response.errorMessage())
                                .data(response)
                                .build());
                    }
                });
    }

    /**
     * Gửi message đến nhiều topics cùng lúc
     * Demo CompletableFuture.allOf pattern
     */
    @PostMapping("/multi-topic")
    public CompletableFuture<ResponseEntity<ApiResponse<Object>>> sendToMultipleTopics(
            @RequestParam List<String> topics,
            @RequestParam(required = false) String key,
            @RequestBody String message) {
        
        return kafkaProducer.sendToMultipleTopics(topics, key, message)
                .thenApply(responses -> {
                    long successCount = responses.stream()
                            .filter(KafkaProducerAdvanced.KafkaSendResponse::success)
                            .count();
                    
                    return ResponseEntity.ok(ApiResponse.builder()
                            .code(200)
                            .message(String.format("Sent to %d/%d topics successfully", 
                                    successCount, topics.size()))
                            .data(responses)
                            .build());
                });
    }

    // ============================================================================
    // BATCH SEND
    // ============================================================================
    
    /**
     * Gửi batch messages
     * Kafka producer sẽ tự động batch theo cấu hình
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Object>> sendBatch(
            @RequestParam String topic,
            @RequestBody List<String> messages) {
        
        var result = kafkaProducer.sendBatchSync(topic, messages);
        
        return ResponseEntity.ok(ApiResponse.builder()
                .code(200)
                .message(String.format("Batch sent - Success: %d, Failed: %d", 
                        result.successCount(), result.failCount()))
                .data(result)
                .build());
    }

    /**
     * Gửi batch messages với keys
     * Message cùng key sẽ vào cùng partition -> đảm bảo ordering
     */
    @PostMapping("/batch-with-keys")
    public ResponseEntity<ApiResponse<String>> sendBatchWithKeys(
            @RequestParam String topic,
            @RequestBody List<KeyValueRequest> messages) {
        
        var kvMessages = messages.stream()
                .map(req -> new KafkaProducerAdvanced.KeyValueMessage(req.key(), req.value()))
                .toList();
        
        kafkaProducer.sendBatchWithKeys(topic, kvMessages);
        
        return ResponseEntity.accepted().body(ApiResponse.<String>builder()
                .code(202)
                .message("Batch with keys submitted")
                .data(String.format("Submitted %d messages", messages.size()))
                .build());
    }

    // ============================================================================
    // WITH HEADERS
    // ============================================================================
    
    /**
     * Gửi message với headers (metadata)
     * Headers dùng cho correlation ID, tracing, etc.
     */
    @PostMapping("/with-headers")
    public CompletableFuture<ResponseEntity<ApiResponse<Object>>> sendWithHeaders(
            @RequestParam String topic,
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "application/json") String contentType,
            @RequestBody String message) {
        
        String correlationId = UUID.randomUUID().toString();
        
        return kafkaProducer.sendWithHeaders(topic, key, message, correlationId, contentType)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.builder()
                        .code(200)
                        .message("Message with headers sent")
                        .data(new SendResultWithHeaders(
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                correlationId
                        ))
                        .build()));
    }

    /**
     * Gửi message với tracing headers
     * Dùng cho distributed tracing
     */
    @PostMapping("/with-tracing")
    public CompletableFuture<ResponseEntity<ApiResponse<Object>>> sendWithTracing(
            @RequestParam String topic,
            @RequestParam(required = false) String key,
            @RequestBody String message) {
        
        String traceId = UUID.randomUUID().toString();
        String spanId = UUID.randomUUID().toString().substring(0, 16);
        
        return kafkaProducer.sendWithTracing(topic, key, message, traceId, spanId)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.builder()
                        .code(200)
                        .message("Message with tracing sent")
                        .data(new TracingResult(traceId, spanId))
                        .build()));
    }

    // ============================================================================
    // TO SPECIFIC PARTITION
    // ============================================================================
    
    /**
     * Gửi message đến partition cụ thể
     * Thường dùng cho testing hoặc special routing
     */
    @PostMapping("/to-partition")
    public CompletableFuture<ResponseEntity<ApiResponse<Object>>> sendToPartition(
            @RequestParam String topic,
            @RequestParam int partition,
            @RequestParam(required = false) String key,
            @RequestBody String message) {
        
        return kafkaProducer.sendToPartition(topic, partition, key, message)
                .thenApply(result -> ResponseEntity.ok(ApiResponse.builder()
                        .code(200)
                        .message("Message sent to specific partition")
                        .data(new SendResult(
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                result.getRecordMetadata().timestamp()
                        ))
                        .build()));
    }

    // ============================================================================
    // PERFORMANCE TEST
    // ============================================================================
    
    /**
     * Test throughput - gửi nhiều messages nhanh
     * Dùng để benchmark Kafka producer
     */
    @PostMapping("/performance-test")
    public ResponseEntity<ApiResponse<Object>> performanceTest(
            @RequestParam String topic,
            @RequestParam(defaultValue = "1000") int messageCount) {
        
        long startTime = System.currentTimeMillis();
        
        // Gửi fire-and-forget để maximize throughput
        IntStream.range(0, messageCount)
                .forEach(i -> kafkaProducer.sendFireAndForget(topic, 
                        "Performance test message " + i));
        
        // Flush để đảm bảo tất cả được gửi
        kafkaProducer.flush();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double throughput = messageCount / (duration / 1000.0);
        
        return ResponseEntity.ok(ApiResponse.builder()
                .code(200)
                .message("Performance test completed")
                .data(new PerformanceResult(
                        messageCount,
                        duration,
                        throughput
                ))
                .build());
    }

    // ============================================================================
    // INNER CLASSES / RECORDS
    // ============================================================================
    
    record SendResult(String topic, int partition, long offset, long timestamp) {}
    
    record SendResultWithHeaders(String topic, int partition, long offset, String correlationId) {}
    
    record TracingResult(String traceId, String spanId) {}
    
    record PerformanceResult(int messageCount, long durationMs, double messagesPerSecond) {}
    
    record KeyValueRequest(String key, String value) {}
}
