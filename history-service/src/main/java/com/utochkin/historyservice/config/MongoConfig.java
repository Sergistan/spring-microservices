package com.utochkin.historyservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.Arrays;
//для локального запуска
//@Configuration
//public class MongoConfig extends AbstractMongoClientConfiguration {
//    @Override
//    protected String getDatabaseName() {
//        return "HistoryServiceDB";
//    }
//
//    @Override
//    public MongoCustomConversions customConversions() {
//        return new MongoCustomConversions(Arrays.asList(
//                new LocalDateTimeToStringConverter(),
//                new StringToLocalDateTimeConverter()
//        ));
//    }
//}

/**
 * Настройка MongoDB: конвертеры LocalDateTime <-> String для корректной сериализации дат
 * в документах истории (пишем ISO-строку, читаем обратно в LocalDateTime).
 */
@Configuration
public class MongoConfig {
    /** Регистрация конвертеров LocalDateTime <-> ISO-строка для документов MongoDB. */
    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new LocalDateTimeToStringConverter(),
                new StringToLocalDateTimeConverter()
        ));
    }
}