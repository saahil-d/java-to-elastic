package com.example.elastic_demo.service;


import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class SearchService {

    private static final String INDEX_NAME = "pgsql_elastic_test_1"; // Your index name

    @Autowired
    private RestHighLevelClient client;

    public String searchDocuments() throws IOException {
        // Start the timer
        long startTime = System.nanoTime();

        // Create a search request for the specified index
        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // Build the query
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .should(QueryBuilders.termsQuery("inventory_items_json.l_type.keyword", "Standard", "Model"))
                .should(QueryBuilders.termsQuery("ks_coreswduration_json.pl.keyword", "1A"))
                .should(QueryBuilders.termQuery("largedatatable_json.active", true))
                .mustNot(QueryBuilders.termQuery("largedatatable_json.active", false))
                .mustNot(QueryBuilders.termQuery("inventory_items_json.enabled_flag", "N"))
                .must(QueryBuilders.termQuery("active", true))

                ;

        sourceBuilder.query(boolQuery);
        sourceBuilder.from(0);
        sourceBuilder.size(20);
        sourceBuilder.sort("product_code.keyword", SortOrder.DESC);

        searchRequest.source(sourceBuilder);

        // Execute the search request
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        // End the timer
        long endTime = System.nanoTime();

        // Calculate elapsed time
        long duration = endTime - startTime;
        StringBuilder result = new StringBuilder();
        result.append("Search took: ").append(duration / 1_000_000).append(" milliseconds\n");

        // Process and append the search results
        long totalHits = searchResponse.getHits().getTotalHits().value;
        result.append("Total hits: ").append(totalHits).append("\n");

        for (SearchHit hit : searchResponse.getHits().getHits()) {
            result.append("Document ID: ").append(hit.getId()).append("\n");
            result.append("Document Source: ").append(hit.getSourceAsString()).append("\n");
        }

        return result.toString();
    }
}
