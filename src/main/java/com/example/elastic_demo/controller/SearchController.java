package com.example.elastic_demo.controller;


import com.example.elastic_demo.model.SearchResult;
import com.example.elastic_demo.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
public class SearchController {

    @Autowired
    private SearchService searchService;

    @PostMapping("/search")
    public ResponseEntity<?> searchDocuments(@RequestBody Map<String, Object> queryParams) {
        try {
            SearchResult result = searchService.searchDocuments(queryParams);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error executing search: " + e.getMessage());
        }
    }

    @GetMapping("/count")
    public ResponseEntity<?> countDocuments() {
        try {
            long count = searchService.countDocuments();
            return ResponseEntity.ok("Total count: " + count);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error executing count: " + e.getMessage());
        }
    }
}
