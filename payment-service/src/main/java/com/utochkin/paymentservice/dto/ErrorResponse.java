package com.utochkin.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Стандартное тело ошибки всех 4xx/5xx ответов (поле messageError). */
@Data
@AllArgsConstructor
public class ErrorResponse {
    private String messageError;
}
