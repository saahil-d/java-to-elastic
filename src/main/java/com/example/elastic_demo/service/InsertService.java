package com.example.elastic_demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.elasticsearch._types.Time;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


@Service
public class InsertService {
    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String jdbcUser;

    @Value("${spring.datasource.password}")
    private String jdbcPassword;

    public void performBulkInsert() {

            String esHost = "localhost";
            int esPort = 9200;
            String esIndex = "pgsql_elastic_test_1";
            int batchSize = 40000;
            int numThreads = Runtime.getRuntime().availableProcessors();
            System.out.println("Number of threads: " + numThreads);
            try (RestClient restClient = RestClient.builder(new HttpHost(esHost, esPort)).build();
                 RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
                 ElasticsearchClient esClient = new ElasticsearchClient(transport)) {

                // Initialize Elasticsearch index
                initializeElasticsearchIndex(esClient, esIndex);

                // Get ID range from database
                IdRange idRange = getProductIdRange(jdbcUrl, jdbcUser, jdbcPassword);
                if (idRange.start > idRange.end) {
                    throw new Exception("No records to process.");
                }

                // Divide ID ranges for parallel processing
                List<IdRange> ranges = calculateRanges(idRange.start, idRange.end, numThreads);

                ExecutorService executor = Executors.newFixedThreadPool(numThreads);
                List<Future<?>> futures = new ArrayList<>();

                // Disable refresh interval
                esClient.indices().putSettings(r -> r.index(esIndex)
                        .settings(s -> s.refreshInterval(Time.of(t -> t.time("-1")))));

                for (IdRange range : ranges) {
                    futures.add(executor.submit(() ->
                            processIdRange(range, jdbcUrl, jdbcUser, jdbcPassword, esClient, esIndex, batchSize)
                    ));
                }

                // Wait for all tasks to complete
                for (Future<?> future : futures) {
                    future.get();
                }
                executor.shutdown();

                // Re-enable refresh interval
                esClient.indices().putSettings(r -> r.index(esIndex)
                        .settings(s -> s.refreshInterval(Time.of(t -> t.time("1s"))).numberOfReplicas("1")));
            } catch (Exception e) {
                throw new RuntimeException("Error during bulk insert: " + e.getMessage(), e);
            }
    }


    private IdRange getProductIdRange(String jdbcUrl, String user, String password) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT MIN(id), MAX(id) FROM product");
            rs.next();
            return new IdRange(rs.getLong(1), rs.getLong(2));
        }
    }

    private List<IdRange> calculateRanges(long minId, long maxId, int numChunks) {
        List<IdRange> ranges = new ArrayList<>();
        long totalIds = maxId - minId + 1;
        long chunkSize = (totalIds + numChunks - 1) / numChunks;

        long current = minId;
        while (current <= maxId) {
            long end = Math.min(current + chunkSize - 1, maxId);
            ranges.add(new IdRange(current, end));
            current = end + 1;
        }
        return ranges;
    }

    private void initializeElasticsearchIndex(ElasticsearchClient client, String index) throws IOException {
        try {
            client.indices().delete(DeleteIndexRequest.of(d -> d.index(index)));
        } catch (IOException ignored) {}
        client.indices().create(CreateIndexRequest.of(c -> c.index(index)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                )));
    }

    private void processIdRange(IdRange range, String jdbcUrl, String user, String password,
                                ElasticsearchClient esClient, String index, int batchSize) {
        // Your processing logic remains the same as the original method
        ObjectMapper objectMapper = new ObjectMapper();

        String sql = "SELECT p.*, " +
                "to_json(i) AS inventory_items_json, " +
                "to_json(k) AS ks_coreswduration_json, " +
                "to_json(l) AS largedatatable_json " +
                "FROM product p " +
                "LEFT JOIN inventory_items i ON p.product_code = i.product_number " +
                "LEFT JOIN ks_coreswduration k ON p.product_code = k.hwmodel " +
                "LEFT JOIN largedatatable l ON p.product_code = l.product_code " +
                "WHERE p.id BETWEEN ? AND ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, range.start);
            stmt.setLong(2, range.end);

            try (ResultSet rs = stmt.executeQuery()) {
                List<BulkOperation> bulkOperations = new ArrayList<>();
                int count = 0;

                while (rs.next()) {
                    Map<String, Object> doc = new HashMap<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String columnName = meta.getColumnName(i);
                        Object columnValue = rs.getObject(i);

                        if (columnName.endsWith("_json") && columnValue != null) {
                            // Parse JSON string to JSON object
                            JsonNode jsonNode = objectMapper.readTree(columnValue.toString());
                            doc.put(columnName, jsonNode);
                        } else {

                            doc.put(columnName, columnValue); // Add the column value directly without parsing
                        }                    }

                    bulkOperations.add(BulkOperation.of(o -> o
                            .index(i -> i
                                    .index(index)
                                    .id(doc.get("product_code").toString())
                                    .document(doc)
                            )));

                    if (++count % batchSize == 0) {
                        sendBulkRequest(esClient, bulkOperations);
                        bulkOperations.clear();
                    }
                }

                if (!bulkOperations.isEmpty()) {
                    sendBulkRequest(esClient, bulkOperations);
                }
            }
        } catch (SQLException | IOException e) {
            System.err.println("Error processing range " + range.start + "-" + range.end + ": " + e.getMessage());
        }
    }
    private static void sendBulkRequest(ElasticsearchClient client, List<BulkOperation> operations) throws IOException {
        BulkResponse response = client.bulk(BulkRequest.of(b -> b.operations(operations)));
        if (response.errors()) {
            response.items().forEach(item -> {
                if (item.error() != null) {
                    System.err.println("Failed document " + item.id() + ": " + item.error().reason());
                }
            });
        }
    }

    static class IdRange {
        final long start;
        final long end;

        IdRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }
}
