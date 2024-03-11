package sample;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
public class SecurityController {
    private static final Logger logger = LoggerFactory.getLogger(SecurityController.class);
    private static final String INDEX_NAME = "security-idx";

    private final JedisPooled jedis;

    public SecurityController(JedisPooled jedis) {
        this.jedis = jedis;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Security {
        private String securityId;
        private String securityName;
        private String securityType;
        private String cusip;
        private String symbol;
        private String isin;
        private String optionType;
        private String optionStyle;

        public Security(String securityId, String securityName, String securityType, String cusip, String symbol, String isin, String optionType, String optionStyle) {
            this.securityId = securityId;
            this.securityName = securityName;
            this.securityType = securityType;
            this.cusip = cusip;
            this.symbol = symbol;
            this.isin = isin;
            this.optionType = optionType;
            this.optionStyle = optionStyle;
        }

        public Security() {
        }

        public String getSecurityId() {
            return securityId;
        }

        public void setSecurityId(String securityId) {
            this.securityId = securityId;
        }

        public String getSecurityName() {
            return securityName;
        }

        public void setSecurityName(String securityName) {
            this.securityName = securityName;
        }

        public String getSecurityType() {
            return securityType;
        }

        public void setSecurityType(String securityType) {
            this.securityType = securityType;
        }

        public String getCusip() {
            return cusip;
        }

        public void setCusip(String cusip) {
            this.cusip = cusip;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getIsin() {
            return isin;
        }

        public void setIsin(String isin) {
            this.isin = isin;
        }

        public String getOptionType() {
            return optionType;
        }

        public void setOptionType(String optionType) {
            this.optionType = optionType;
        }

        public String getOptionStyle() {
            return optionStyle;
        }

        public void setOptionStyle(String optionStyle) {
            this.optionStyle = optionStyle;
        }
    }

    private void createIndex(JedisPooled client) {
        try {
            client.ftDropIndex(INDEX_NAME);
        } catch (Exception e) {
            // Throws exception if index not there; we ignore
        }

        Schema schema = new Schema().addTagField("$securityId").as("securityId")
                .addTagField("$.cusip").as("cusip")
                .addTextField("$.securityName", 1.0).as("securityName")
                .addTagField("$.isin").as("isin")
                .addTagField("$.symbol").as("symbol");
        IndexDefinition rule = new IndexDefinition(IndexDefinition.Type.JSON)
                .setPrefixes(new String[]{"securities:"});
        client.ftCreate(INDEX_NAME, IndexOptions.defaultOptions().setDefinition(rule), schema);
    }

    private Security getSecurity(String fileName) {
        Resource equityResource = new ClassPathResource(fileName);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(equityResource.getFile(), Security.class);
        } catch (IOException e) {
            System.out.println("Error reading file " + e.getMessage());
        }
        return null;
    }

    private class SearchValue {
        private String name;
        private String symbol;
        private String id;
        private String cusip;
        private String isin;

        public SearchValue(String name, String symbol, String id, String cusip, String isin) {
            this.name = name;
            this.symbol = symbol;
            this.id = id;
            this.cusip = cusip;
            this.isin = isin;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCusip() {
            return cusip;
        }

        public void setCusip(String cusip) {
            this.cusip = cusip;
        }

        public String getIsin() {
            return isin;
        }

        public void setIsin(String isin) {
            this.isin = isin;
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

    @GetMapping("/loadsec")
    public String load() {
        // Load the data
        List<Security> securities = new ArrayList<>();

        Security security = getSecurity("equity.json");
        securities.add(security);

        security = getSecurity("digasset.json");
        securities.add(security);

        security = getSecurity("option.json");
        securities.add(security);

        long startTime = System.currentTimeMillis();

        // Drop and create the index
        createIndex(jedis);

        // Create a pipeline object so we can batch Redis commands
        Pipeline pipeline = jedis.pipelined();
        int numSent = 0;
        ObjectMapper om = new ObjectMapper();

        for (int i = 0; i < securities.size(); i++) {
            try {
                Security sec = securities.get(i);
                logger.info(String.format("Inserting %s with symbol %s", sec.getSecurityId(), sec.getSymbol()));
                pipeline.jsonSet("securities:" + sec.getSecurityId(), om.writeValueAsString(sec));
            } catch (JsonProcessingException e) {
                logger.info(String.format("Exception with json object %s", e.getMessage()));
            }

            // Send commands in a batch of 1000 (or whatever your favorite number is)
            if (numSent > 0 && numSent % 1000 == 0) {
                pipeline.sync();
            }
            numSent++;
        }
        pipeline.sync();

        long insertDoneTime = System.currentTimeMillis();

        return String.format("Inserting %d objects took %d milliseconds", numSent, insertDoneTime - startTime);
    }

    @GetMapping("/searchsec")
    public SearchResponse search(@RequestParam(required = false, defaultValue = "*") String term) {
        List<SearchValue> results = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        long searchTime = 0;

        String queryString = "";

        if ("*".equals(term)) {
            queryString = "*";
        } else {
            // search for name OR ID OR cusip OR isin OR symbol
            queryString = String.format("(@securityName:%s)|(@securityId:{%s})|(@cusip:{%s})|(@isin:{%s})|(@symbol:{%s})", term, term, term, term, term);
        }
        Query q = new Query(queryString);

        int numFound = 0;
        try {
            SearchResult res = jedis.ftSearch(INDEX_NAME, q);
            List<Document> docs = res.getDocuments();
            long endTime = System.currentTimeMillis();

            numFound = docs.size();
            for (Document doc : docs) {
                ObjectMapper om = new ObjectMapper();
                try {
                    Security sec = om.readValue(doc.getString("$"), Security.class);
                    SearchValue val = new SearchValue(sec.getSecurityName(), sec.getSymbol(), sec.getSecurityId(), sec.getCusip(), sec.getIsin());
                    results.add(val);
                } catch (JsonProcessingException e) {
                    logger.info("Failed to parse JSON result");
                }
            }
            searchTime = endTime - startTime;
            logger.info(String.format("Searching for %s took %d milliseconds and returned %d docs", term, searchTime, docs.size()));
        } catch (JedisDataException jedisDataException) {
            logger.info(String.format("JedisDataException %s", jedisDataException.getMessage()));
        }

        SearchResponse result = new SearchResponse(numFound, searchTime, results);

        return result;
    }

}
