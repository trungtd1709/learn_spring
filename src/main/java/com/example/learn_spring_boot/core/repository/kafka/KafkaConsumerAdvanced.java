package com.example.learn_spring_boot.core.repository.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Kafka Consumer nâng cao với nhiều pattern xử lý message:
 * 
 * 1. Single Message Processing - Xử lý từng message một
 * 2. Batch Processing - Xử lý nhiều message cùng lúc
 * 3. Concurrent Processing - Nhiều consumer thread
 * 4. Async Processing - Xử lý bất đồng bộ với CompletableFuture
 * 5. Manual Acknowledgment - Kiểm soát offset commit
 * 6. With Headers - Đọc metadata từ headers
 * 7. Error Handling - Xử lý lỗi và retry
 * 
 * LƯU Ý QUAN TRỌNG:
 * - Mỗi @KafkaListener method tương ứng với một use case
 * - Trong thực tế, chỉ nên có 1 consumer cho mỗi topic
 * - Các ví dụ dưới đây dùng các topic khác nhau để demo
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerAdvanced {

    private final KafkaMessageHandler messageHandler;
    private final Executor kafkaAsyncExecutor;

    // ============================================================================
    // CASE 1: SINGLE MESSAGE - Xử lý từng message với manual ack
    // ============================================================================
    
    /**
     * Consumer cơ bản - xử lý từng message một với manual acknowledgment
     * 
     * Đặc điểm:
     * - containerFactory = "singleMessageListenerFactory": Dùng factory đã config
     * - Acknowledgment: Cho phép commit offset thủ công
     * - Simple và dễ hiểu, phù hợp cho hầu hết use case
     * 
     * Tại sao dùng Manual Ack?
     * - Đảm bảo offset chỉ được commit SAU KHI xử lý thành công
     * - Nếu xử lý fail và không ack -> message sẽ được re-deliver
     * - Tránh mất message khi consumer crash
     * 
     * Flow:
     * 1. Nhận message từ Kafka
     * 2. Xử lý business logic
     * 3. Nếu thành công -> acknowledge() để commit offset
     * 4. Nếu fail -> không acknowledge -> message sẽ được poll lại
     */
    @KafkaListener(
            topics = "single-message-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "singleMessageListenerFactory"
    )
    public void consumeSingleMessage(
            @Payload String message,
            @org.springframework.messaging.handler.annotation.Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @org.springframework.messaging.handler.annotation.Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        
        log.info("Received single message - Partition: {}, Offset: {}, Message: {}", 
                partition, offset, message);
        
        try {
            // ===== BUSINESS LOGIC START =====
            // Xử lý message ở đây
            messageHandler.processMessage(message);
            // ===== BUSINESS LOGIC END =====
            
            // Chỉ acknowledge khi xử lý THÀNH CÔNG
            // Sau khi ack, offset được commit -> Kafka biết message đã được xử lý
            ack.acknowledge();
            
            log.debug("Message acknowledged - Partition: {}, Offset: {}", partition, offset);
            
        } catch (Exception e) {
            // KHÔNG acknowledge khi có lỗi
            // Message sẽ được re-deliver trong lần poll tiếp theo
            log.error("Error processing message - Partition: {}, Offset: {}, Error: {}", 
                    partition, offset, e.getMessage());
            
            // Có thể throw exception để trigger error handler
            // hoặc implement custom retry logic
            throw e;
        }
    }

    /**
     * Consumer với đầy đủ thông tin từ ConsumerRecord
     * Dùng khi cần truy cập nhiều metadata của message
     */
    @KafkaListener(
            topics = "detailed-message-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "singleMessageListenerFactory"
    )
    public void consumeWithFullDetails(
            ConsumerRecord<String, String> record,
            Acknowledgment ack) {
        
        // ConsumerRecord chứa tất cả thông tin về message
        log.info("Received record - Topic: {}, Partition: {}, Offset: {}, " +
                        "Key: {}, Timestamp: {}, Value: {}", 
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.timestamp(),
                record.value());
        
        // Đọc headers nếu có
        record.headers().forEach(header -> 
            log.debug("Header - Key: {}, Value: {}", 
                    header.key(), 
                    new String(header.value(), StandardCharsets.UTF_8)));
        
        try {
            messageHandler.processMessage(record.value());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing record", e);
            throw e;
        }
    }

    // ============================================================================
    // CASE 2: BATCH PROCESSING - Xử lý nhiều message cùng lúc
    // ============================================================================
    
    /**
     * Consumer xử lý batch message
     * 
     * Đặc điểm:
     * - containerFactory = "batchListenerFactory": Factory config cho batch
     * - Nhận List<ConsumerRecord> thay vì single message
     * - Acknowledgment commit cho TOÀN BỘ batch
     * 
     * Tại sao dùng Batch?
     * - Throughput cao hơn nhiều so với single message
     * - Giảm overhead của commit offset (commit 1 lần cho cả batch)
     * - Hiệu quả cho batch insert vào database
     * - Giảm network round-trips
     * 
     * CẢNH BÁO:
     * - Nếu 1 message trong batch fail -> cả batch có thể được re-process
     * - Cần cẩn thận với idempotency (xử lý message trùng lặp)
     * - Memory usage cao hơn (phải giữ cả batch trong memory)
     * 
     * Flow:
     * 1. Poll lấy batch messages (max = MAX_POLL_RECORDS_CONFIG)
     * 2. Xử lý từng message hoặc cả batch
     * 3. Acknowledge để commit offset của message cuối cùng trong batch
     */
    @KafkaListener(
            topics = "batch-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "batchListenerFactory"
    )
    public void consumeBatch(
            List<ConsumerRecord<String, String>> records,
            Acknowledgment ack) {
        
        log.info("Received batch of {} messages", records.size());
        
        try {
            // ===== OPTION 1: Xử lý từng message trong batch =====
            for (ConsumerRecord<String, String> record : records) {
                log.debug("Processing - Partition: {}, Offset: {}", 
                        record.partition(), record.offset());
                messageHandler.processMessage(record.value());
            }
            
            // ===== OPTION 2: Xử lý cả batch cùng lúc (batch insert, etc.) =====
            // List<String> messages = records.stream()
            //         .map(ConsumerRecord::value)
            //         .collect(Collectors.toList());
            // messageHandler.processBatch(messages);
            
            // Acknowledge sau khi xử lý TOÀN BỘ batch thành công
            // Commit offset của message CUỐI CÙNG trong batch
            ack.acknowledge();
            
            log.info("Batch processed and acknowledged - Size: {}", records.size());
            
        } catch (Exception e) {
            // Nếu fail, cả batch sẽ được re-deliver
            log.error("Error processing batch of {} messages: {}", 
                    records.size(), e.getMessage());
            throw e;
        }
    }

    /**
     * Batch consumer với partial acknowledgment
     * Xử lý từng message và track những message nào fail
     */
    @KafkaListener(
            topics = "batch-partial-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "batchListenerFactory"
    )
    public void consumeBatchWithPartialAck(
            List<ConsumerRecord<String, String>> records,
            Acknowledgment ack) {
        
        int successCount = 0;
        int failCount = 0;
        
        for (ConsumerRecord<String, String> record : records) {
            try {
                messageHandler.processMessage(record.value());
                successCount++;
            } catch (Exception e) {
                // Log error nhưng tiếp tục xử lý các message còn lại
                log.error("Failed to process message - Partition: {}, Offset: {}, Error: {}", 
                        record.partition(), record.offset(), e.getMessage());
                failCount++;
                
                // Có thể gửi đến Dead Letter Topic ở đây
                // deadLetterProducer.send(record);
            }
        }
        
        log.info("Batch processing completed - Success: {}, Failed: {}", successCount, failCount);
        
        // Vẫn acknowledge để move forward
        // Message lỗi đã được xử lý (gửi DLT, log, etc.)
        ack.acknowledge();
    }

    // ============================================================================
    // CASE 3: CONCURRENT PROCESSING - Nhiều consumer threads
    // ============================================================================
    
    /**
     * Consumer với nhiều concurrent threads
     * 
     * Đặc điểm:
     * - containerFactory = "concurrentListenerFactory": 3 consumer threads
     * - Mỗi thread được assign một số partition
     * - Kafka đảm bảo: 1 partition chỉ được 1 consumer xử lý
     * 
     * Cách hoạt động:
     * - VD: Topic có 6 partitions, concurrency = 3
     * - Thread 1 xử lý partition 0, 1
     * - Thread 2 xử lý partition 2, 3
     * - Thread 3 xử lý partition 4, 5
     * 
     * Tại sao dùng Concurrency?
     * - Tăng throughput bằng parallel processing
     * - Tận dụng multi-core CPU
     * - Giảm lag khi có nhiều message
     * 
     * LƯU Ý QUAN TRỌNG:
     * - Thứ tự được đảm bảo TRONG MỖI PARTITION
     * - KHÔNG đảm bảo thứ tự GIỮA CÁC PARTITION
     * - Nếu cần xử lý theo thứ tự, dùng cùng key -> cùng partition
     * - concurrency KHÔNG nên > số partition (threads sẽ idle)
     */
    @KafkaListener(
            topics = "concurrent-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "concurrentListenerFactory"
    )
    public void consumeConcurrent(
            @Payload String message,
            @org.springframework.messaging.handler.annotation.Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @org.springframework.messaging.handler.annotation.Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        
        // Thread.currentThread().getName() cho thấy thread nào đang xử lý
        log.info("Concurrent consumer - Thread: {}, Partition: {}, Offset: {}", 
                Thread.currentThread().getName(), partition, offset);
        
        try {
            messageHandler.processMessage(message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error in concurrent consumer - Thread: {}, Error: {}", 
                    Thread.currentThread().getName(), e.getMessage());
            throw e;
        }
    }

    // ============================================================================
    // CASE 4: BATCH + CONCURRENT - Kết hợp batch và đa luồng
    // ============================================================================
    
    /**
     * Consumer kết hợp batch và concurrent
     * Đây là cấu hình cho throughput CAO NHẤT
     * 
     * Cách hoạt động:
     * - Có 3 consumer threads (concurrency=3)
     * - Mỗi thread nhận batch từ partition của nó
     * - Xử lý batch song song trên các threads
     * 
     * Use case:
     * - High-throughput systems
     * - Log aggregation
     * - Metrics/Events collection
     * - Message không cần thứ tự nghiêm ngặt
     */
    @KafkaListener(
            topics = "batch-concurrent-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "batchConcurrentListenerFactory"
    )
    public void consumeBatchConcurrent(
            List<ConsumerRecord<String, String>> records,
            Acknowledgment ack) {
        
        log.info("Batch concurrent - Thread: {}, Batch size: {}", 
                Thread.currentThread().getName(), records.size());
        
        try {
            // Xử lý batch
            for (ConsumerRecord<String, String> record : records) {
                messageHandler.processMessage(record.value());
            }
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error in batch concurrent - Thread: {}", 
                    Thread.currentThread().getName(), e);
            throw e;
        }
    }

    // ============================================================================
    // CASE 5: ASYNC PROCESSING với CompletableFuture
    // ============================================================================
    
    /**
     * Consumer với async processing sử dụng CompletableFuture
     * 
     * Đặc điểm:
     * - Consumer thread nhận message và dispatch đến thread pool
     * - Không block consumer thread -> có thể poll message tiếp
     * - Xử lý nhiều message song song từ CÙNG 1 PARTITION
     * 
     * Tại sao dùng Async?
     * - Tăng throughput khi xử lý message chậm (API call, IO)
     * - Không block consumer thread
     * - Có thể xử lý nhiều message parallel
     * 
     * CẢNH BÁO QUAN TRỌNG:
     * - KHÔNG đảm bảo thứ tự xử lý (message sau có thể xong trước)
     * - Offset commit phức tạp (phải đợi tất cả async tasks hoàn thành)
     * - Có thể xảy ra: message B commit trước message A
     * - Chỉ dùng khi THỨ TỰ KHÔNG QUAN TRỌNG
     * 
     * Flow:
     * 1. Consumer thread nhận message
     * 2. Submit task đến async executor
     * 3. Consumer thread return ngay (không block)
     * 4. Async thread xử lý message
     * 5. Khi hoàn thành, acknowledge message
     */
    @KafkaListener(
            topics = "async-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "asyncListenerFactory"
    )
    public void consumeAsync(
            ConsumerRecord<String, String> record,
            Acknowledgment ack) {
        
        log.info("Async consumer received - Thread: {}, Partition: {}, Offset: {}", 
                Thread.currentThread().getName(), record.partition(), record.offset());
        
        // Dispatch message đến async executor
        // Consumer thread return ngay, không đợi xử lý xong
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Async processing - Thread: {}, Offset: {}", 
                        Thread.currentThread().getName(), record.offset());
                
                // Giả lập xử lý chậm (API call, IO operation)
                messageHandler.processMessageWithDelay(record.value(), 1000);
                
                // Acknowledge sau khi xử lý xong
                // LƯU Ý: Đây là potential issue - xem explanation bên dưới
                ack.acknowledge();
                
                log.debug("Async processing completed - Offset: {}", record.offset());
                
            } catch (Exception e) {
                log.error("Async processing failed - Offset: {}, Error: {}", 
                        record.offset(), e.getMessage());
                // Không acknowledge -> message sẽ được re-deliver
                // Nhưng các message sau có thể đã được acknowledge!
            }
        }, kafkaAsyncExecutor);
        
        // Consumer thread return ngay
        log.debug("Async task submitted - Offset: {}", record.offset());
    }

    /**
     * Async consumer với CompletableFuture chaining
     * Demo cách chain nhiều async operations
     */
    @KafkaListener(
            topics = "async-chain-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "asyncListenerFactory"
    )
    public void consumeAsyncWithChaining(
            ConsumerRecord<String, String> record,
            Acknowledgment ack) {
        
        // Chain nhiều async operations
        CompletableFuture
                // Step 1: Validate message
                .supplyAsync(() -> {
                    log.debug("Step 1: Validating - Thread: {}", Thread.currentThread().getName());
                    return messageHandler.validateMessage(record.value());
                }, kafkaAsyncExecutor)
                
                // Step 2: Transform message
                .thenApplyAsync(validatedMsg -> {
                    log.debug("Step 2: Transforming - Thread: {}", Thread.currentThread().getName());
                    return messageHandler.transformMessage(validatedMsg);
                }, kafkaAsyncExecutor)
                
                // Step 3: Save to database
                .thenAcceptAsync(transformedMsg -> {
                    log.debug("Step 3: Saving - Thread: {}", Thread.currentThread().getName());
                    messageHandler.saveMessage(transformedMsg);
                }, kafkaAsyncExecutor)
                
                // Handle completion
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        ack.acknowledge();
                        log.info("Async chain completed successfully - Offset: {}", record.offset());
                    } else {
                        log.error("Async chain failed - Offset: {}, Error: {}", 
                                record.offset(), ex.getMessage());
                        // Không acknowledge -> sẽ retry
                    }
                });
    }

    /**
     * Async batch processing - xử lý batch message song song
     * Mỗi message trong batch được xử lý async
     */
    @KafkaListener(
            topics = "async-batch-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "batchListenerFactory"
    )
    public void consumeAsyncBatch(
            List<ConsumerRecord<String, String>> records,
            Acknowledgment ack) {
        
        log.info("Async batch consumer - Received {} messages", records.size());
        
        // Tạo CompletableFuture cho mỗi message trong batch
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = records.stream()
                .map(record -> CompletableFuture.runAsync(() -> {
                    log.debug("Async processing record - Partition: {}, Offset: {}, Thread: {}", 
                            record.partition(), record.offset(), Thread.currentThread().getName());
                    messageHandler.processMessage(record.value());
                }, kafkaAsyncExecutor))
                .toArray(CompletableFuture[]::new);
        
        // Đợi TẤT CẢ futures hoàn thành rồi mới acknowledge
        // Đảm bảo không có message nào bị bỏ sót
        CompletableFuture.allOf(futures)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        // Tất cả message xử lý thành công
                        ack.acknowledge();
                        log.info("Async batch completed - All {} messages processed", records.size());
                    } else {
                        // Có ít nhất 1 message fail
                        // Không acknowledge -> cả batch sẽ được retry
                        log.error("Async batch failed - Error: {}", ex.getMessage());
                    }
                })
                // Block để đảm bảo không return trước khi xử lý xong
                // Nếu không, Kafka có thể commit offset trước khi xử lý hoàn thành
                .join();
    }

    // ============================================================================
    // CASE 6: WITH HEADERS - Đọc metadata từ headers
    // ============================================================================
    
    /**
     * Consumer đọc và xử lý headers
     * Headers thường dùng cho: correlation ID, tracing, content type, etc.
     */
    @KafkaListener(
            topics = "headers-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "singleMessageListenerFactory"
    )
    public void consumeWithHeaders(
            ConsumerRecord<String, String> record,
            Acknowledgment ack) {
        
        // Đọc headers từ message
        String correlationId = getHeaderValue(record, "correlation-id");
        String contentType = getHeaderValue(record, "content-type");
        String traceId = getHeaderValue(record, "X-Trace-Id");
        
        log.info("Received message with headers - CorrelationId: {}, ContentType: {}, TraceId: {}", 
                correlationId, contentType, traceId);
        
        try {
            // Set correlation ID vào context cho logging/tracing
            // MDC.put("correlationId", correlationId);
            
            messageHandler.processMessage(record.value());
            ack.acknowledge();
            
        } finally {
            // MDC.remove("correlationId");
        }
    }

    /**
     * Helper method để đọc header value
     */
    private String getHeaderValue(ConsumerRecord<String, String> record, String headerKey) {
        org.apache.kafka.common.header.Header header = record.headers().lastHeader(headerKey);
        if (header != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }

    // ============================================================================
    // CASE 7: ERROR HANDLING - Xử lý lỗi và retry
    // ============================================================================
    
    /**
     * Consumer với error handling và manual retry
     */
    @KafkaListener(
            topics = "retry-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "singleMessageListenerFactory"
    )
    public void consumeWithRetry(
            ConsumerRecord<String, String> record,
            Acknowledgment ack) {
        
        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;
        
        // Manual retry loop
        while (retryCount < maxRetries) {
            try {
                messageHandler.processMessage(record.value());
                ack.acknowledge();
                log.info("Message processed successfully after {} attempts", retryCount + 1);
                return; // Success - exit method
                
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("Processing failed - Attempt {}/{}, Error: {}", 
                        retryCount, maxRetries, e.getMessage());
                
                if (retryCount < maxRetries) {
                    // Exponential backoff before retry
                    try {
                        long waitTime = (long) Math.pow(2, retryCount) * 100; // 200ms, 400ms, 800ms
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // All retries exhausted - send to DLT (Dead Letter Topic)
        log.error("All {} retries exhausted - Sending to DLT. Partition: {}, Offset: {}", 
                maxRetries, record.partition(), record.offset());
        
        // TODO: Send to Dead Letter Topic
        // deadLetterProducer.send("retry-topic.DLT", record.key(), record.value());
        
        // Acknowledge để không retry vô hạn
        // Message đã được gửi đến DLT để xử lý manual
        ack.acknowledge();
    }

    // ============================================================================
    // CASE 8: FILTERING - Lọc message trước khi xử lý
    // ============================================================================
    
    /**
     * Consumer với record filter
     * Chỉ xử lý message thỏa mãn điều kiện
     * 
     * LƯU Ý: Việc filter này xảy ra SAU khi message đã được poll
     * Để filter trước khi poll, cần dùng RecordFilterStrategy trong factory config
     */
    @KafkaListener(
            topics = "filtered-topic",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "singleMessageListenerFactory"
    )
    public void consumeWithFilter(
            @Payload String message,
            Acknowledgment ack) {
        
        // Filter: chỉ xử lý message bắt đầu bằng "IMPORTANT:"
        if (message == null || !message.startsWith("IMPORTANT:")) {
            log.debug("Message filtered out: {}", message);
            // Vẫn phải acknowledge để skip message này
            ack.acknowledge();
            return;
        }
        
        log.info("Processing important message: {}", message);
        
        try {
            messageHandler.processMessage(message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing important message", e);
            throw e;
        }
    }
}
