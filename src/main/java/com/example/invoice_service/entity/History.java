package com.example.invoice_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "history")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class History {
    private static final int MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String type;
    @Column(length = 2000)
    private String value;
    @Column(length = 2000)
    private String value2;
    private String status;
    @Column(length = 2000)
    private String detailsError;
    private String createdDate = LocalDateTime.now().toString();

    public static class HistoryBuilder {

        public HistoryBuilder value(String value) {
            this.value = truncate(value);
            return this;
        }

        public HistoryBuilder value2(String value2) {
            this.value2 = truncate(value2);
            return this;
        }

        private static String truncate(String input) {
            if (input == null) return null;
            return input.length() <= MAX_LENGTH
                    ? input
                    : input.substring(0, MAX_LENGTH);
        }
    }
}
