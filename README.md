# spring-search

A Spring-Boot webapp with RESTful endpoints to demonstrate Redis Search with Java and Jedis

## Prerequisites

- Java 21 though may work with earlier versions
- Redis

## Run

```bash
./gradlew :jedis-spring:bootRun
```
## Build and run as a jar
```bash
./gradlew build
java -jar build/libs/jedis-spring-1.0.jar
```

Tomcat server is running at http://localhost:8080

## Code

See [SearchController.java](src/main/java/sample/SearchController.java) for the code to load and index data, and to execute search queries