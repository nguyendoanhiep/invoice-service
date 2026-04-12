package com.example.invoice_service.entity.response;

import java.util.List;

public class WooResponse<T> {
    private List<T> data;
    private int total;
    private int totalPages;

    public WooResponse(List<T> data, int total, int totalPages) {
        this.data = data;
        this.total = total;
        this.totalPages = totalPages;
    }

    public List<T> getData() { return data; }
    public int getTotal() { return total; }
    public int getTotalPages() { return totalPages; }
}

