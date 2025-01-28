package com.example.elastic_demo.service;


import com.example.elastic_demo.model.SearchResult;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private static final String INDEX_NAME = "pgsql_elastic_test_1"; // Your index name

    @Autowired
    private RestHighLevelClient client;

    public SearchResult searchDocuments(Map<String, Object> queryParams) throws IOException {
        // Start the timer
        long startTime = System.nanoTime();

        // Create a search request for the specified index
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // Build the query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // Add should clauses
        List<Map<String, Object>> shouldClauses = (List<Map<String, Object>>) queryParams.get("should");
        if (shouldClauses != null) {
            for (Map<String, Object> clause : shouldClauses) {
                boolQuery.should(QueryBuilders.termsQuery((String) clause.get("field"), (List<String>) clause.get("values")));
            }
        }

        // Add must clauses
        List<Map<String, Object>> mustClauses = (List<Map<String, Object>>) queryParams.get("must");
        if (mustClauses != null) {
            for (Map<String, Object> clause : mustClauses) {
                boolQuery.must(QueryBuilders.termQuery((String) clause.get("field"), clause.get("value")));
            }
        }

        // Add must not clauses
        List<Map<String, Object>> mustNotClauses = (List<Map<String, Object>>) queryParams.get("mustNot");
        if (mustNotClauses != null) {
            for (Map<String, Object> clause : mustNotClauses) {
                boolQuery.mustNot(QueryBuilders.termQuery((String) clause.get("field"), clause.get("value")));
            }
        }

        sourceBuilder.query(boolQuery);

        // Set from and size
        sourceBuilder.from((int) queryParams.getOrDefault("from", 0));
        sourceBuilder.size((int) queryParams.getOrDefault("size", 20));

        // Set sort
        Map<String, String> sort = (Map<String, String>) queryParams.get("sort");
        if (sort != null) {
            sourceBuilder.sort(sort.get("field"), SortOrder.fromString(sort.get("order")));
        }

        searchRequest.source(sourceBuilder);

        // Execute the search request
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        // End the timer
        long endTime = System.nanoTime();

        // Calculate elapsed time
        long duration = endTime - startTime;

        // Build the search result
        SearchResult searchResult = new SearchResult();
        searchResult.setTook(duration / 1_000_000); // Convert to milliseconds
        searchResult.setTotalHits(searchResponse.getHits().getTotalHits().value);

        List<Map<String, Object>> documents = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            documents.add(hit.getSourceAsMap());
        }
        searchResult.setDocuments(documents);

        return searchResult;
    }


    public long countDocuments() throws IOException {
        // Create a count request for the specified index
        CountRequest countRequest = new CountRequest(INDEX_NAME);

        // Build the query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .should(QueryBuilders.termsQuery("inventory_items_json.l_type.keyword", "Standard", "Model"))
                .should(QueryBuilders.termsQuery("ks_coreswduration_json.pl.keyword", "1A"))
                .should(QueryBuilders.termQuery("largedatatable_json.active", true))
                .mustNot(QueryBuilders.termQuery("largedatatable_json.active", false))
                .mustNot(QueryBuilders.termQuery("inventory_items_json.enabled_flag", "N"))
                .must(QueryBuilders.termQuery("active", true));

        // Set the query in the count request
        countRequest.query(boolQuery);

        // Execute the count request
        long count = client.count(countRequest, RequestOptions.DEFAULT).getCount();

        // Return the total count
        return count;
    }
}
