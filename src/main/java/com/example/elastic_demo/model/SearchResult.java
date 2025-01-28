package com.example.elastic_demo.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;



public class SearchResult {
    @Getter
    @Setter
    private long took;

    @Getter
    @Setter
    private long totalHits;

    @Getter
    @Setter
    private List<Map<String, Object>> documents;
}