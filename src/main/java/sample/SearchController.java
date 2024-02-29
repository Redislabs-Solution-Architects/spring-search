package sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@RestController
public class SearchController {
    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final JedisPooled jedis;

    public SearchController(JedisPooled jedis) {
        this.jedis = jedis;
    }

    private void createIndex(JedisPooled client) {
        try {
            client.ftDropIndex("idx1");
        } catch (Exception e) {
            // Throws exception if index not there; we ignore
        }

        Schema schema = new Schema().addTagField("acctId")
                .addTagField("trxnId")
                .addTextField("description", 1.0)
                .addNumericField("amount");
        IndexDefinition rule = new IndexDefinition(IndexDefinition.Type.HASH)
                .setPrefixes(new String[]{"transactions:"});
        client.ftCreate("idx1", IndexOptions.defaultOptions().setDefinition(rule), schema);
    }

    private List<String> getDescriptions() {
        List<String> descriptions = new ArrayList<>();

        Resource companyDataResource = new ClassPathResource("descriptions.txt");

        BufferedReader reader;

        try {
            reader = new BufferedReader(new FileReader(companyDataResource.getFile()));
            String line = reader.readLine();

            while (line != null) {
                System.out.println(line);
                descriptions.add(line);
                // read next line
                line = reader.readLine();
            }

            reader.close();
        } catch (IOException e) {
            System.out.println("Error reading file " + e.getMessage());
        }

        return descriptions;
    }

    private static final double MIN_AMOUNT = 0.1;
    private static final double MAX_AMOUNT = 10000.0;
    private static final int ID_LENGTH = 8;

    private class SearchValue {
        public String trxnId;
        public String description;
        public String amount;

        public SearchValue(String trxnId, String description, String amount) {
            this.trxnId = trxnId;
            this.description = description;
            this.amount = amount;
        }

        public String getTrxnId() {
            return trxnId;
        }

        public void setTrxnId(String trxnId) {
            this.trxnId = trxnId;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(String amount) {
            this.amount = amount;
        }
    }

    private class SearchResponse {
        public int numFound;
        public long time;
        public List<SearchValue> values;

        public SearchResponse(int numFound, long time, List<SearchValue> values) {
            this.numFound = numFound;
            this.time = time;
            this.values = values;
        }
    }

    @GetMapping("/load")
    public String load() {
        // Load the data
        List<String> descriptions = getDescriptions();

        long startTime = System.currentTimeMillis();

        // Drop and create the index
        createIndex(jedis);

        // Create a pipeline object so we can batch Redis commands
        Pipeline pipeline = jedis.pipelined();
        int numSent = 0;

        for (int i = 0; i < descriptions.size(); i++) {
            String acctId = UUID.randomUUID().toString().substring(0, ID_LENGTH);
            String trxnId = UUID.randomUUID().toString().substring(0, ID_LENGTH);

            Random r = new Random();
            double amount = MIN_AMOUNT + r.nextDouble() * (MAX_AMOUNT - MIN_AMOUNT);

            // Now add some data to Redis as a hash
            Map<String, Object> fields = new HashMap<>();
            fields.put("acctId", acctId);
            fields.put("trxnId", trxnId);
            fields.put("description", descriptions.get(i));
            fields.put("amount", amount);

            pipeline.hset("transactions:" + trxnId, RediSearchUtil.toStringMap(fields));

            // Send commands in a batch of 1000 (or whatever your favorite number is)
            if (numSent > 0 && numSent % 1000 == 0) {
                pipeline.sync();
            }
            numSent++;
        }
        pipeline.sync();

        long insertDoneTime = System.currentTimeMillis();

        logger.info(String.format("Inserting %d objects took %d milliseconds", numSent, insertDoneTime - startTime));

        return String.format("Inserting %d objects took %d milliseconds", numSent, insertDoneTime - startTime);
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam(required = false, defaultValue = "*") String term,
                                    @RequestParam(required = false, defaultValue = "0") String amount) {
        List<SearchValue> results = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        long searchTime = 0;
        String queryString = term;

        double minAmount = 0.0;

        if (amount != null) {
            minAmount = Double.parseDouble(amount);
        }
        if (minAmount > 0.0) {
            if ("*".equals(term)) {
                queryString = String.format("@amount:[%f, %f]", minAmount, Float.MAX_VALUE);
            } else {
                queryString = String.format("@description:%s @amount:[%f,%f]", term, minAmount, Float.MAX_VALUE);
            }
        }
        logger.info(String.format("Query string %s", queryString));

        int numFound = 0;
        Query q = new Query(queryString).limit(0, 40);
        try {
            SearchResult res = jedis.ftSearch("idx1", q);
            List<Document> docs = res.getDocuments();
            long endTime = System.currentTimeMillis();

            numFound = docs.size();
            for (Document doc : docs) {
                SearchValue val = new SearchValue(doc.getString("trxnId"), doc.getString("description"), doc.getString("amount"));
                results.add(val);
            }
            searchTime = endTime - startTime;
            logger.info(String.format("Searching for %s took %d milliseconds and returned %d docs", term, searchTime, docs.size()));
        } catch (JedisDataException jedisDataException) {
            // Could be no index found
        }

        SearchResponse result = new SearchResponse(numFound, searchTime, results);

        return result;
    }

}
