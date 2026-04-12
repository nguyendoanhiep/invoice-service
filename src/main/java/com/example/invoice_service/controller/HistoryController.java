package com.example.invoice_service.controller;


import com.example.invoice_service.repository.HistoryRepository;
import com.example.invoice_service.entity.response.ApiResponse;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
@NoArgsConstructor
public class HistoryController {
    @Autowired
    HistoryRepository historyRepository;

    @GetMapping
    public ApiResponse<?> getAll(@RequestParam(name = "page" , defaultValue = "1") int page,
                                      @RequestParam(name = "size" , defaultValue = "10") int size,
                                      @RequestParam(name ="type", required = false) String type,
                                      @RequestParam(name = "status",required = false)  String status,
                                      @RequestParam(name = "fromDate") String fromDate,
                                      @RequestParam(name = "toDate") String toDate){
        return ApiResponse.builder().data(historyRepository.getAll(PageRequest.of(page-1 , size),type,status ,fromDate,toDate)).build();
    }
}
