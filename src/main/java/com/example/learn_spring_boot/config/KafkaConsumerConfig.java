package com.example.learn_spring_boot.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Cấu hình Kafka Consumer với nhiều factory khác nhau để xử lý các trường hợp:
 * 1. Single message processing - Xử lý từng message một
 * 2. Batch processing - Xử lý nhiều message cùng lúc
 * 3. Concurrent processing - Xử lý đa luồng với nhiều consumer
 * 4. Manual acknowledgment - Xác nhận message thủ công để đảm bảo message được xử lý
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Tạo các properties cơ bản cho Consumer
     * Đây là method helper để tái sử dụng cấu hình chung
     */
    private Map<String, Object> baseConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        
        // Địa chỉ Kafka broker để kết nối
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Deserializer cho key và value - chuyển đổi byte[] thành String
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // Group ID - các consumer cùng group sẽ chia sẻ partition
        // Mỗi partition chỉ được 1 consumer trong group xử lý
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // AUTO_OFFSET_RESET_CONFIG: Xác định vị trí bắt đầu đọc khi không có offset
        // - "earliest": Đọc từ đầu topic (offset 0)
        // - "latest": Chỉ đọc message mới
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // ENABLE_AUTO_COMMIT_CONFIG: Tự động commit offset
        // - true: Kafka tự động đánh dấu message đã được đọc
        // - false: Phải commit thủ công (dùng khi cần đảm bảo xử lý thành công)
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        return props;
    }

    // ============================================================================
    // CASE 1: SINGLE MESSAGE CONSUMER - Xử lý từng message một
    // ============================================================================
    
    /**
     * ConsumerFactory cơ bản cho xử lý từng message
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConsumerConfigs());
    }

    /**
     * Factory cho single message processing
     * Sử dụng khi muốn xử lý từng message riêng lẻ
     * 
     * Ưu điểm:
     * - Đơn giản, dễ debug
     * - Dễ track lỗi từng message
     * 
     * Nhược điểm:
     * - Throughput thấp hơn batch
     * - Overhead cao khi có nhiều message nhỏ
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> singleMessageListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // MANUAL_IMMEDIATE: Commit ngay sau khi gọi acknowledge()
        // Đảm bảo message chỉ được đánh dấu xử lý xong khi logic business hoàn thành
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Concurrency = 1: Chỉ 1 consumer thread
        // Tăng số này nếu muốn nhiều thread xử lý song song
        factory.setConcurrency(1);
        
        return factory;
    }

    // ============================================================================
    // CASE 2: BATCH MESSAGE CONSUMER - Xử lý nhiều message cùng lúc
    // ============================================================================
    
    /**
     * ConsumerFactory cho batch processing
     * Cấu hình thêm các tham số để fetch nhiều message
     */
    @Bean
    public ConsumerFactory<String, String> batchConsumerFactory() {
        Map<String, Object> props = baseConsumerConfigs();
        
        // MAX_POLL_RECORDS_CONFIG: Số message tối đa trong 1 lần poll
        // Giá trị cao = batch lớn hơn, throughput cao hơn
        // Nhưng cần cân nhắc memory và thời gian xử lý
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        
        // FETCH_MIN_BYTES_CONFIG: Số byte tối thiểu để broker trả về
        // Broker sẽ đợi đến khi có đủ data hoặc timeout
        // Giúp tăng efficiency khi có ít message
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        
        // FETCH_MAX_WAIT_MS_CONFIG: Thời gian tối đa đợi để có đủ FETCH_MIN_BYTES
        // Cân bằng giữa latency và throughput
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Factory cho batch message processing
     * Sử dụng khi cần xử lý nhiều message cùng lúc để tăng throughput
     * 
     * Ưu điểm:
     * - Throughput cao hơn nhiều so với single
     * - Hiệu quả cho các operation có overhead cố định (DB batch insert)
     * - Giảm số lần commit offset
     * 
     * Nhược điểm:
     * - Phức tạp hơn khi xử lý lỗi từng message
     * - Memory usage cao hơn
     * - Latency có thể cao hơn (đợi đủ batch)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(batchConsumerFactory());
        
        // BẮT BUỘC: Enable batch listener để nhận List<ConsumerRecord>
        factory.setBatchListener(true);
        
        // MANUAL: Commit sau khi toàn bộ batch được xử lý xong
        // Khác với MANUAL_IMMEDIATE - chỉ commit 1 lần cho cả batch
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        return factory;
    }

    // ============================================================================
    // CASE 3: CONCURRENT CONSUMER - Nhiều consumer thread xử lý song song
    // ============================================================================
    
    /**
     * Factory cho concurrent processing với nhiều consumer thread
     * Mỗi thread sẽ được assign một hoặc nhiều partition
     * 
     * LƯU Ý QUAN TRỌNG:
     * - Số concurrency không nên vượt quá số partition của topic
     * - Nếu concurrency > partitions: Một số thread sẽ idle
     * - Kafka đảm bảo: 1 partition chỉ được 1 consumer trong group xử lý
     * 
     * Ưu điểm:
     * - Tăng throughput bằng parallel processing
     * - Tận dụng tối đa multi-core CPU
     * - Vẫn đảm bảo thứ tự trong từng partition
     * 
     * Nhược điểm:
     * - Không đảm bảo thứ tự giữa các partition
     * - Cần quản lý thread-safety trong business logic
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> concurrentListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Concurrency = 3: Tạo 3 consumer thread
        // Mỗi thread có thể xử lý 1 hoặc nhiều partition
        // VD: Topic có 6 partition -> mỗi thread xử lý 2 partition
        factory.setConcurrency(3);
        
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        return factory;
    }

    // ============================================================================
    // CASE 4: BATCH + CONCURRENT - Kết hợp batch và đa luồng
    // ============================================================================
    
    /**
     * Factory kết hợp cả batch processing và concurrent consumers
     * Đây là cấu hình cho throughput cao nhất
     * 
     * Cách hoạt động:
     * - Mỗi consumer thread nhận 1 batch message từ partition của nó
     * - Xử lý batch trong thread riêng
     * - Commit sau khi batch hoàn thành
     * 
     * Use case:
     * - High throughput systems (log aggregation, metrics collection)
     * - Batch insert vào database
     * - Message không cần xử lý theo thứ tự nghiêm ngặt
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchConcurrentListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(batchConsumerFactory());
        
        factory.setBatchListener(true);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        return factory;
    }

    // ============================================================================
    // CASE 5: ASYNC CONSUMER - Xử lý bất đồng bộ với ThreadPool riêng
    // ============================================================================
    
    /**
     * ThreadPool executor cho async processing
     * Consumer thread sẽ dispatch message đến pool này để xử lý
     * 
     * Ưu điểm:
     * - Không block consumer thread
     * - Có thể xử lý nhiều message song song từ cùng 1 partition
     * - Kiểm soát được số thread xử lý
     * 
     * CẢNH BÁO:
     * - KHÔNG đảm bảo thứ tự xử lý message
     * - Cần cẩn thận với offset commit (message sau có thể hoàn thành trước)
     * - Chỉ dùng khi thứ tự không quan trọng
     */
    @Bean(name = "kafkaAsyncExecutor")
    public Executor kafkaAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size: Số thread tối thiểu luôn sẵn sàng
        executor.setCorePoolSize(5);
        
        // Max pool size: Số thread tối đa khi queue đầy
        executor.setMaxPoolSize(10);
        
        // Queue capacity: Số task có thể đợi trong queue
        // Khi queue đầy và đang ở max pool size -> reject policy được áp dụng
        executor.setQueueCapacity(100);
        
        // Thread name prefix: Giúp debug và monitor dễ dàng
        executor.setThreadNamePrefix("kafka-async-");
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        return executor;
    }

    /**
     * Factory sử dụng async executor
     * Consumer nhận message và dispatch đến thread pool xử lý
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> asyncListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // AckMode.RECORD: Commit sau mỗi record
        // Phù hợp với async vì mỗi record xử lý độc lập
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        
        return factory;
    }

    // ============================================================================
    // CASE 6: ERROR HANDLING - Xử lý lỗi và retry
    // ============================================================================
    
    /**
     * Factory với error handling và retry
     * Tự động retry khi gặp exception
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> retryListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // CommonErrorHandler có thể được thêm để xử lý retry và dead letter topic
        // factory.setCommonErrorHandler(errorHandler());
        
        return factory;
    }
}
