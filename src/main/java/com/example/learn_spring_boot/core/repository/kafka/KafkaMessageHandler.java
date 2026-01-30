package com.example.learn_spring_boot.core.repository.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service xử lý Kafka message với các pattern khác nhau
 * 
 * Class này chứa business logic để xử lý message
 * Được tách riêng khỏi consumer để:
 * 1. Dễ test (có thể mock)
 * 2. Reusable cho nhiều consumers
 * 3. Separation of concerns
 * 4. Có thể thêm @Async để xử lý bất đồng bộ
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaMessageHandler {

    // ============================================================================
    // BASIC MESSAGE PROCESSING
    // ============================================================================
    
    /**
     * Xử lý message cơ bản
     * Đây là method chính để xử lý business logic
     * 
     * @param message Nội dung message
     */
    public void processMessage(String message) {
        log.info("Processing message: {}", message);
        
        // ===== BUSINESS LOGIC START =====
        // Thực hiện xử lý message ở đây:
        // - Parse JSON
        // - Validate data
        // - Save to database
        // - Call external API
        // - etc.
        // ===== BUSINESS LOGIC END =====
        
        // Giả lập một số processing time
        simulateProcessing(50);
        
        log.debug("Message processed successfully");
    }

    /**
     * Xử lý message với delay (giả lập heavy processing)
     * Dùng cho demo async processing
     * 
     * @param message   Nội dung message
     * @param delayMs   Thời gian delay (milliseconds)
     */
    public void processMessageWithDelay(String message, long delayMs) {
        log.info("Processing message with {}ms delay - Thread: {}", 
                delayMs, Thread.currentThread().getName());
        
        simulateProcessing(delayMs);
        
        log.debug("Delayed processing completed");
    }

    // ============================================================================
    // BATCH PROCESSING
    // ============================================================================
    
    /**
     * Xử lý batch messages
     * Phù hợp cho batch insert vào database
     * 
     * Ưu điểm của batch processing:
     * - Giảm overhead của database connections
     * - Giảm số lần commit transaction
     * - Tăng throughput đáng kể
     * 
     * @param messages List các message cần xử lý
     */
    public void processBatch(List<String> messages) {
        log.info("Processing batch of {} messages", messages.size());
        
        // ===== BATCH PROCESSING OPTIONS =====
        
        // Option 1: Xử lý từng message
        // for (String message : messages) {
        //     processMessage(message);
        // }
        
        // Option 2: Batch insert vào database
        // repository.saveAll(messages.stream()
        //         .map(this::parseMessage)
        //         .collect(Collectors.toList()));
        
        // Option 3: Parallel stream processing
        // messages.parallelStream().forEach(this::processMessage);
        
        // Giả lập batch processing
        simulateProcessing(messages.size() * 10L);
        
        log.info("Batch processing completed - {} messages", messages.size());
    }

    /**
     * Batch processing với CompletableFuture
     * Mỗi message trong batch được xử lý parallel
     * 
     * @param messages List messages
     * @return CompletableFuture khi tất cả hoàn thành
     */
    public CompletableFuture<Void> processBatchAsync(List<String> messages) {
        log.info("Async batch processing - {} messages", messages.size());
        
        // Tạo array of futures
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = messages.stream()
                .map(msg -> CompletableFuture.runAsync(() -> processMessage(msg)))
                .toArray(CompletableFuture[]::new);
        
        // Return future that completes when all complete
        return CompletableFuture.allOf(futures)
                .thenRun(() -> log.info("All {} messages in batch processed", messages.size()));
    }

    // ============================================================================
    // ASYNC PROCESSING với @Async
    // ============================================================================
    
    /**
     * Xử lý message bất đồng bộ với Spring @Async
     * 
     * Để sử dụng @Async:
     * 1. Thêm @EnableAsync vào @Configuration class
     * 2. Method phải được gọi từ bên ngoài class (Spring AOP proxy)
     * 3. Method phải trả về void hoặc Future/CompletableFuture
     * 
     * LƯU Ý: @Async không hoạt động khi gọi internal (this.method())
     * Phải inject bean và gọi qua bean reference
     * 
     * @param message Nội dung message
     * @return CompletableFuture<Void>
     */
    @Async("kafkaAsyncExecutor")
    public CompletableFuture<Void> processMessageAsync(String message) {
        log.info("Async processing (Spring @Async) - Thread: {}", 
                Thread.currentThread().getName());
        
        // Giả lập heavy processing
        simulateProcessing(500);
        
        processMessage(message);
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Async processing với result
     * Trả về kết quả xử lý
     */
    @Async("kafkaAsyncExecutor")
    public CompletableFuture<ProcessingResult> processMessageAsyncWithResult(String message) {
        log.info("Async processing with result - Thread: {}", 
                Thread.currentThread().getName());
        
        try {
            simulateProcessing(500);
            return CompletableFuture.completedFuture(
                    new ProcessingResult(true, message, "Processed successfully")
            );
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    new ProcessingResult(false, message, e.getMessage())
            );
        }
    }

    // ============================================================================
    // MESSAGE TRANSFORMATION PIPELINE
    // ============================================================================
    
    /**
     * Validate message
     * Step 1 trong processing pipeline
     * 
     * @param message Raw message
     * @return Validated message
     * @throws IllegalArgumentException nếu message không hợp lệ
     */
    public String validateMessage(String message) {
        log.debug("Validating message");
        
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
        
        // Thêm các validation rules khác:
        // - Check JSON format
        // - Check required fields
        // - Check data types
        // - etc.
        
        return message.trim();
    }

    /**
     * Transform message
     * Step 2 trong processing pipeline
     * 
     * @param message Validated message
     * @return Transformed message
     */
    public String transformMessage(String message) {
        log.debug("Transforming message");
        
        // Thực hiện transform:
        // - Parse JSON
        // - Map fields
        // - Enrich data
        // - Format output
        
        // Ví dụ đơn giản: convert to uppercase
        return message.toUpperCase();
    }

    /**
     * Save message
     * Step 3 trong processing pipeline
     * 
     * @param message Transformed message
     */
    public void saveMessage(String message) {
        log.debug("Saving message to database");
        
        // Save to database:
        // Entity entity = mapper.toEntity(message);
        // repository.save(entity);
        
        simulateProcessing(100); // Giả lập DB write
        
        log.debug("Message saved successfully");
    }

    // ============================================================================
    // COMPLETABLE FUTURE PATTERNS
    // ============================================================================
    
    /**
     * Full async pipeline với CompletableFuture
     * Demo cách chain multiple async operations
     * 
     * Pattern: supplyAsync -> thenApplyAsync -> thenAcceptAsync
     * 
     * @param message Raw message
     * @return CompletableFuture<ProcessingResult>
     */
    public CompletableFuture<ProcessingResult> processWithPipeline(String message) {
        return CompletableFuture
                // Step 1: Validate (có thể throw exception)
                .supplyAsync(() -> {
                    log.debug("Pipeline Step 1: Validate - Thread: {}", 
                            Thread.currentThread().getName());
                    return validateMessage(message);
                })
                // Step 2: Transform
                .thenApplyAsync(validated -> {
                    log.debug("Pipeline Step 2: Transform - Thread: {}", 
                            Thread.currentThread().getName());
                    return transformMessage(validated);
                })
                // Step 3: Save (returns void, wrap in supplier)
                .thenApplyAsync(transformed -> {
                    log.debug("Pipeline Step 3: Save - Thread: {}", 
                            Thread.currentThread().getName());
                    saveMessage(transformed);
                    return transformed;
                })
                // Step 4: Create result
                .thenApply(saved -> {
                    log.info("Pipeline completed successfully");
                    return new ProcessingResult(true, saved, "Success");
                })
                // Handle exceptions
                .exceptionally(ex -> {
                    log.error("Pipeline failed: {}", ex.getMessage());
                    return new ProcessingResult(false, message, ex.getMessage());
                });
    }

    /**
     * Combine multiple async operations
     * Xử lý message đồng thời theo nhiều cách rồi aggregate
     */
    public CompletableFuture<CombinedResult> processWithCombine(String message) {
        // Operation 1: Save to DB
        CompletableFuture<Boolean> dbFuture = CompletableFuture.supplyAsync(() -> {
            log.debug("Saving to DB - Thread: {}", Thread.currentThread().getName());
            saveMessage(message);
            return true;
        });
        
        // Operation 2: Send to another service
        CompletableFuture<Boolean> serviceFuture = CompletableFuture.supplyAsync(() -> {
            log.debug("Calling external service - Thread: {}", Thread.currentThread().getName());
            simulateProcessing(200);
            return true;
        });
        
        // Operation 3: Update cache
        CompletableFuture<Boolean> cacheFuture = CompletableFuture.supplyAsync(() -> {
            log.debug("Updating cache - Thread: {}", Thread.currentThread().getName());
            simulateProcessing(50);
            return true;
        });
        
        // Combine all results
        return dbFuture
                .thenCombine(serviceFuture, (dbResult, serviceResult) -> 
                        new PartialResult(dbResult, serviceResult))
                .thenCombine(cacheFuture, (partial, cacheResult) -> 
                        new CombinedResult(partial.dbSuccess(), partial.serviceSuccess(), cacheResult));
    }

    /**
     * Any-of pattern - return khi BẤT KỲ operation nào hoàn thành
     * Useful cho: Multiple data sources, first-response-wins
     */
    public CompletableFuture<String> processWithAnyOf(String message) {
        // Thử lấy data từ multiple sources
        CompletableFuture<String> source1 = CompletableFuture.supplyAsync(() -> {
            simulateProcessing(100);
            return "Result from source 1";
        });
        
        CompletableFuture<String> source2 = CompletableFuture.supplyAsync(() -> {
            simulateProcessing(50);
            return "Result from source 2";
        });
        
        CompletableFuture<String> source3 = CompletableFuture.supplyAsync(() -> {
            simulateProcessing(200);
            return "Result from source 3";
        });
        
        // Return cái nào xong trước
        return CompletableFuture.anyOf(source1, source2, source3)
                .thenApply(result -> (String) result);
    }

    /**
     * Timeout pattern - xử lý với timeout
     */
    public CompletableFuture<ProcessingResult> processWithTimeout(String message, long timeoutMs) {
        return CompletableFuture
                .supplyAsync(() -> {
                    processMessage(message);
                    return new ProcessingResult(true, message, "Success");
                })
                // Timeout sau timeoutMs milliseconds
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                // Handle timeout exception
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof java.util.concurrent.TimeoutException) {
                        log.error("Processing timeout after {}ms", timeoutMs);
                        return new ProcessingResult(false, message, "Timeout");
                    }
                    return new ProcessingResult(false, message, ex.getMessage());
                });
    }

    /**
     * Retry pattern với CompletableFuture
     */
    public CompletableFuture<ProcessingResult> processWithRetry(
            String message, int maxRetries) {
        
        return retryAsync(() -> {
            processMessage(message);
            return new ProcessingResult(true, message, "Success");
        }, maxRetries, 100);
    }

    /**
     * Generic retry helper
     */
    private <T> CompletableFuture<T> retryAsync(
            java.util.function.Supplier<T> supplier, 
            int maxRetries, 
            long delayMs) {
        
        return CompletableFuture.supplyAsync(supplier)
                .exceptionallyCompose(ex -> {
                    if (maxRetries > 0) {
                        log.warn("Retrying... remaining attempts: {}", maxRetries);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return retryAsync(supplier, maxRetries - 1, delayMs * 2);
                    }
                    return CompletableFuture.failedFuture(ex);
                });
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================
    
    /**
     * Giả lập processing time
     */
    private void simulateProcessing(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Processing interrupted", e);
        }
    }

    // ============================================================================
    // INNER CLASSES / RECORDS
    // ============================================================================
    
    /**
     * Kết quả xử lý message
     */
    public record ProcessingResult(
            boolean success,
            String message,
            String details
    ) {}

    /**
     * Kết quả partial cho combine
     */
    private record PartialResult(
            boolean dbSuccess,
            boolean serviceSuccess
    ) {}

    /**
     * Kết quả combined từ nhiều operations
     */
    public record CombinedResult(
            boolean dbSuccess,
            boolean serviceSuccess,
            boolean cacheSuccess
    ) {
        public boolean allSuccess() {
            return dbSuccess && serviceSuccess && cacheSuccess;
        }
    }
}
