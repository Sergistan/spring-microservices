package com.utochkin.historyservice.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Маркер обработанного Kafka-события (event_id из OrderDtoForKafka, _id коллекции processed_events):
 * вставка маркера и запись истории — в одной операции; DuplicateKeyException = повторная доставка
 * at-least-once → консьюмер просто подтверждает offset без дубля в orderHistory.
 * PostgreSQL-аналог — processed_events в delivery-service (там же механика, другая СУБД).
 */
@Setter
@Getter
@Document(collection = "processed_events")
@AllArgsConstructor
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    private String eventId;

    private LocalDateTime processedAt;

}
