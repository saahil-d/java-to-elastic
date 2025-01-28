package com.example.elastic_demo.controller;

import com.example.elastic_demo.service.InsertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InsertController {

    @Autowired
    private InsertService elasticsearchService;

    @GetMapping("/run-bulk-insert")
    public ResponseEntity<String> runBulkInsert() {
        try {
            elasticsearchService.performBulkInsert();
            return ResponseEntity.ok("All records inserted successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}
