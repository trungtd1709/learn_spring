package com.example.learn_spring_boot.core.repository.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Kafka Producer nâng cao với nhiều phương thức gửi message:
 * 
 * 1. Fire-and-forget: Gửi và không quan tâm kết quả
 * 2. Synchronous: Gửi và đợi kết quả
 * 3. Asynchronous với callback: Gửi và xử lý kết quả sau
 * 4. CompletableFuture: Gửi với reactive style
 * 5. Batch sending: Gửi nhiều message cùng lúc
 * 6. Với headers: Gửi message kèm metadata
 * 7. Với partition key: Đảm bảo thứ tự message
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerAdvanced {

    private final KafkaTemplate<String, String> kafkaTemplate;

    // ============================================================================
    // CASE 1: FIRE AND FORGET - Gửi và không quan tâm kết quả
    // ============================================================================
    
    /**
     * Gửi message mà không quan tâm kết quả
     * 
     * Đặc điểm:
     * - Throughput cao nhất vì không đợi response
     * - KHÔNG đảm bảo message được gửi thành công
     * - Phù hợp cho: Metrics, logs, data không quan trọng
     * 
     * CẢNH BÁO: Có thể mất message nếu:
     * - Broker không available
     * - Network issue
     * - Buffer đầy
     * 
     * @param topic   Tên topic
     * @param message Nội dung message
     */
    public void sendFireAndForget(String topic, String message) {
        // Gửi và không quan tâm kết quả
        // Spring Kafka sẽ tự retry theo cấu hình nếu có lỗi
        kafkaTemplate.send(topic, message);
        
        log.debug("Fire-and-forget message sent to topic: {}", topic);
    }

    // ============================================================================
    // CASE 2: SYNCHRONOUS SEND - Gửi và đợi kết quả
    // ============================================================================
    
    /**
     * Gửi message đồng bộ và đợi broker xác nhận
     * 
     * Đặc điểm:
     * - Đảm bảo biết được message đã gửi thành công hay thất bại
     * - Throughput thấp hơn vì phải đợi response
     * - Phù hợp cho: Dữ liệu quan trọng, transaction
     * 
     * Cách hoạt động:
     * 1. Gửi message đến broker
     * 2. Block thread cho đến khi nhận được ack từ broker
     * 3. Trả về metadata (partition, offset, timestamp)
     * 
     * @param topic   Tên topic
     * @param key     Key để partition (message cùng key sẽ vào cùng partition)
     * @param message Nội dung message
     * @return RecordMetadata chứa thông tin về message đã gửi
     * @throws RuntimeException nếu gửi thất bại
     */
    public RecordMetadata sendSync(String topic, String key, String message) {
        try {
            // get() sẽ block cho đến khi có kết quả hoặc exception
            SendResult<String, String> result = kafkaTemplate.send(topic, key, message)
                    .get(10, TimeUnit.SECONDS); // Timeout 10 giây
            
            RecordMetadata metadata = result.getRecordMetadata();
            
            log.info("Sync message sent successfully - Topic: {}, Partition: {}, Offset: {}", 
                    metadata.topic(), 
                    metadata.partition(), 
                    metadata.offset());
            
            return metadata;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending message", e);
        } catch (ExecutionException e) {
            // ExecutionException wrap exception thực sự bên trong
            throw new RuntimeException("Failed to send message: " + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            throw new RuntimeException("Timeout while sending message", e);
        }
    }

    // ============================================================================
    // CASE 3: ASYNCHRONOUS WITH CALLBACK - Gửi bất đồng bộ với callback
    // ============================================================================
    
    /**
     * Gửi message bất đồng bộ và xử lý kết quả qua callback
     * 
     * Đặc điểm:
     * - Không block thread gọi
     * - Vẫn biết được kết quả qua callback
     * - Cân bằng giữa throughput và reliability
     * - Callback chạy trên Kafka producer I/O thread
     * 
     * CẢNH BÁO:
     * - Không nên làm heavy processing trong callback
     * - Callback thread khác với calling thread
     * 
     * @param topic   Tên topic
     * @param key     Key để partition
     * @param message Nội dung message
     */
    public void sendAsync(String topic, String key, String message) {
        // CompletableFuture cho phép chain các operation
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, message);
        
        // Callback xử lý khi hoàn thành (thành công hoặc thất bại)
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // Thành công
                RecordMetadata metadata = result.getRecordMetadata();
                log.info("Async message sent - Topic: {}, Partition: {}, Offset: {}", 
                        metadata.topic(), 
                        metadata.partition(), 
                        metadata.offset());
            } else {
                // Thất bại - log error và có thể retry hoặc gửi đến DLQ
                log.error("Failed to send async message to topic: {} - Error: {}", 
                        topic, 
                        ex.getMessage());
                
                // Có thể implement retry logic hoặc gửi đến dead letter queue ở đây
                handleSendError(topic, key, message, ex);
            }
        });
        
        log.debug("Async send initiated for topic: {}", topic);
    }

    // ============================================================================
    // CASE 4: COMPLETABLE FUTURE - Reactive style với chaining
    // ============================================================================
    
    /**
     * Gửi message và trả về CompletableFuture để caller có thể chain operations
     * 
     * Đặc điểm:
     * - Caller kiểm soát cách xử lý kết quả
     * - Có thể compose nhiều async operations
     * - Hỗ trợ reactive programming style
     * - Dễ dàng kết hợp với các async operations khác
     * 
     * Use case:
     * - Gửi message rồi update database
     * - Gửi đến nhiều topic rồi aggregate kết quả
     * - Pipeline xử lý phức tạp
     * 
     * @param topic   Tên topic
     * @param key     Key để partition
     * @param message Nội dung message
     * @return CompletableFuture có thể được chain với các operations khác
     */
    public CompletableFuture<SendResult<String, String>> sendWithFuture(
            String topic, String key, String message) {
        
        return kafkaTemplate.send(topic, key, message)
                // Transform kết quả: log và trả về
                .thenApply(result -> {
                    log.info("Future message sent - Partition: {}, Offset: {}", 
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    return result;
                })
                // Xử lý exception và convert thành custom exception nếu cần
                .exceptionally(ex -> {
                    log.error("Future send failed: {}", ex.getMessage());
                    throw new RuntimeException("Kafka send failed", ex);
                });
    }

    /**
     * Gửi message và transform kết quả thành custom response
     * Ví dụ về chaining CompletableFuture
     */
    public CompletableFuture<KafkaSendResponse> sendAndGetResponse(
            String topic, String key, String message) {
        
        return kafkaTemplate.send(topic, key, message)
                .thenApply(result -> {
                    RecordMetadata metadata = result.getRecordMetadata();
                    return new KafkaSendResponse(
                            true,
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset(),
                            null
                    );
                })
                .exceptionally(ex -> new KafkaSendResponse(
                        false, topic, -1, -1, ex.getMessage()
                ));
    }

    /**
     * Compose nhiều async sends - gửi đến nhiều topic cùng lúc
     * Ví dụ về parallel CompletableFuture
     * 
     * Đặc điểm:
     * - Gửi đến tất cả topic song song
     * - Đợi tất cả hoàn thành
     * - Aggregate kết quả
     */
    public CompletableFuture<List<KafkaSendResponse>> sendToMultipleTopics(
            List<String> topics, String key, String message) {
        
        // Tạo list các futures cho mỗi topic
        List<CompletableFuture<KafkaSendResponse>> futures = topics.stream()
                .map(topic -> sendAndGetResponse(topic, key, message))
                .collect(Collectors.toList());
        
        // allOf đợi tất cả futures hoàn thành
        // Sau đó collect kết quả
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join) // join() vì đã biết hoàn thành
                        .collect(Collectors.toList()));
    }

    // ============================================================================
    // CASE 5: BATCH SENDING - Gửi nhiều message hiệu quả
    // ============================================================================
    
    /**
     * Gửi nhiều message (batch) một cách hiệu quả
     * 
     * Đặc điểm:
     * - Producer sẽ tự động batch các message theo cấu hình:
     *   + batch.size: Kích thước tối đa của batch (bytes)
     *   + linger.ms: Thời gian đợi để accumulate messages
     * - Gửi nhanh không đợi từng message
     * - Có thể đợi tất cả hoàn thành nếu cần
     * 
     * QUAN TRỌNG:
     * - Kafka producer đã có cơ chế batching nội bộ
     * - Method này chỉ giúp gửi nhiều message nhanh chóng
     * - Thực tế producer sẽ batch theo cấu hình
     * 
     * @param topic    Tên topic
     * @param messages List các message cần gửi
     * @return List CompletableFuture để track từng message nếu cần
     */
    public List<CompletableFuture<SendResult<String, String>>> sendBatch(
            String topic, List<String> messages) {
        
        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>();
        
        for (String message : messages) {
            // Mỗi send() không block, message được đưa vào buffer
            // Producer sẽ tự batch và gửi theo cấu hình
            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(topic, message);
            futures.add(future);
        }
        
        log.info("Batch of {} messages sent to topic: {}", messages.size(), topic);
        
        return futures;
    }

    /**
     * Gửi batch và đợi tất cả hoàn thành
     * Đảm bảo tất cả message được gửi thành công trước khi return
     * 
     * @param topic    Tên topic
     * @param messages List các message
     * @return BatchSendResult chứa thông tin về batch
     */
    public BatchSendResult sendBatchSync(String topic, List<String> messages) {
        List<CompletableFuture<SendResult<String, String>>> futures = sendBatch(topic, messages);
        
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < futures.size(); i++) {
            try {
                // Đợi từng future hoàn thành
                futures.get(i).get(30, TimeUnit.SECONDS);
                successCount++;
            } catch (Exception e) {
                failCount++;
                errors.add("Message " + i + ": " + e.getMessage());
            }
        }
        
        log.info("Batch send completed - Success: {}, Failed: {}", successCount, failCount);
        
        return new BatchSendResult(successCount, failCount, errors);
    }

    /**
     * Gửi batch với key để đảm bảo ordering
     * Message cùng key sẽ vào cùng partition -> đảm bảo thứ tự
     */
    public List<CompletableFuture<SendResult<String, String>>> sendBatchWithKeys(
            String topic, List<KeyValueMessage> messages) {
        
        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>();
        
        for (KeyValueMessage kv : messages) {
            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(topic, kv.key(), kv.value());
            futures.add(future);
        }
        
        return futures;
    }

    // ============================================================================
    // CASE 6: SEND WITH HEADERS - Gửi message với metadata
    // ============================================================================
    
    /**
     * Gửi message kèm theo headers (metadata)
     * 
     * Use case của headers:
     * - Correlation ID: Track message qua nhiều services
     * - Content-Type: Định dạng của message body
     * - Timestamp: Thời gian tạo message
     * - Source: Service gửi message
     * - Version: Schema version
     * - Trace ID: Distributed tracing
     * 
     * Headers KHÔNG ảnh hưởng đến partitioning
     * 
     * @param topic         Tên topic
     * @param key           Key để partition
     * @param message       Nội dung message
     * @param correlationId ID để track message
     * @param contentType   Loại content (json, xml, ...)
     */
    public CompletableFuture<SendResult<String, String>> sendWithHeaders(
            String topic, String key, String message, 
            String correlationId, String contentType) {
        
        // Tạo ProducerRecord để có thể thêm headers
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, message);
        
        // Thêm headers - key là String, value là byte[]
        record.headers()
                .add("correlation-id", correlationId.getBytes(StandardCharsets.UTF_8))
                .add("content-type", contentType.getBytes(StandardCharsets.UTF_8))
                .add("timestamp", String.valueOf(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8))
                .add("source", "learn-spring-boot".getBytes(StandardCharsets.UTF_8));
        
        return kafkaTemplate.send(record)
                .thenApply(result -> {
                    log.info("Message with headers sent - CorrelationId: {}, Partition: {}", 
                            correlationId, 
                            result.getRecordMetadata().partition());
                    return result;
                });
    }

    /**
     * Gửi message với trace context cho distributed tracing
     */
    public CompletableFuture<SendResult<String, String>> sendWithTracing(
            String topic, String key, String message, String traceId, String spanId) {
        
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, message);
        
        // Headers cho distributed tracing (tương thích OpenTelemetry/Zipkin)
        record.headers()
                .add("X-Trace-Id", traceId.getBytes(StandardCharsets.UTF_8))
                .add("X-Span-Id", spanId.getBytes(StandardCharsets.UTF_8))
                .add("X-Parent-Span-Id", spanId.getBytes(StandardCharsets.UTF_8));
        
        return kafkaTemplate.send(record);
    }

    // ============================================================================
    // CASE 7: SEND TO SPECIFIC PARTITION - Gửi đến partition cụ thể
    // ============================================================================
    
    /**
     * Gửi message đến partition cụ thể
     * 
     * CẢNH BÁO: Thường KHÔNG nên dùng cách này vì:
     * - Mất cân bằng load giữa các partition
     * - Khó scale khi thêm partition
     * - Kafka partitioner đã xử lý tốt việc này
     * 
     * Use case hợp lệ:
     * - Testing
     * - Đảm bảo message vào partition đang có consumer
     * - Custom routing logic
     * 
     * @param topic     Tên topic
     * @param partition Số partition (0-based)
     * @param key       Key (vẫn có thể dùng cho logging)
     * @param message   Nội dung message
     */
    public CompletableFuture<SendResult<String, String>> sendToPartition(
            String topic, int partition, String key, String message) {
        
        // Constructor với partition number
        ProducerRecord<String, String> record = 
                new ProducerRecord<>(topic, partition, key, message);
        
        return kafkaTemplate.send(record)
                .thenApply(result -> {
                    log.info("Message sent to specific partition {} - Offset: {}", 
                            partition, 
                            result.getRecordMetadata().offset());
                    return result;
                });
    }

    // ============================================================================
    // CASE 8: TRANSACTIONAL SEND - Gửi trong transaction
    // ============================================================================
    
    /**
     * Gửi nhiều message trong một transaction
     * Đảm bảo all-or-nothing: tất cả thành công hoặc tất cả fail
     * 
     * YÊU CẦU CẤU HÌNH:
     * - spring.kafka.producer.transaction-id-prefix=tx-
     * - Broker phải enable transactions
     * 
     * Use case:
     * - Gửi đến nhiều topic cần atomic
     * - Read-process-write pattern (consume -> process -> produce)
     * - Event sourcing với multiple events
     * 
     * @param operations List các send operations cần thực hiện trong transaction
     */
    public void sendInTransaction(List<TransactionOperation> operations) {
        // executeInTransaction đảm bảo:
        // 1. Begin transaction
        // 2. Execute tất cả operations
        // 3. Commit nếu thành công, rollback nếu có exception
        kafkaTemplate.executeInTransaction(kafkaOps -> {
            for (TransactionOperation op : operations) {
                kafkaOps.send(op.topic(), op.key(), op.message());
            }
            return null;
        });
        
        log.info("Transactional send completed for {} operations", operations.size());
    }

    // ============================================================================
    // HELPER METHODS & CLASSES
    // ============================================================================
    
    /**
     * Xử lý lỗi khi gửi message thất bại
     * Có thể implement retry logic hoặc gửi đến DLQ
     */
    private void handleSendError(String topic, String key, String message, Throwable ex) {
        // Implement error handling strategy:
        // 1. Retry với exponential backoff
        // 2. Gửi đến Dead Letter Queue
        // 3. Lưu vào database để xử lý sau
        // 4. Alert/monitoring
        
        log.error("Handling send error for topic: {} - Will retry or send to DLQ", topic);
    }

    /**
     * Flush producer buffer
     * Đảm bảo tất cả message trong buffer được gửi đi
     * Dùng trước khi shutdown application
     */
    public void flush() {
        kafkaTemplate.flush();
        log.info("Producer buffer flushed");
    }

    // ============================================================================
    // INNER CLASSES / RECORDS
    // ============================================================================
    
    /**
     * Response object cho send operation
     */
    public record KafkaSendResponse(
            boolean success,
            String topic,
            int partition,
            long offset,
            String errorMessage
    ) {}

    /**
     * Result của batch send operation
     */
    public record BatchSendResult(
            int successCount,
            int failCount,
            List<String> errors
    ) {}

    /**
     * Key-Value pair cho batch send với keys
     */
    public record KeyValueMessage(
            String key,
            String value
    ) {}

    /**
     * Operation trong transaction
     */
    public record TransactionOperation(
            String topic,
            String key,
            String message
    ) {}
}
