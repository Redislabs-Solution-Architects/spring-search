# spring-search

A Spring-Boot webapp with RESTful endpoints to demonstrate Redis Search with Java and Jedis

## Prerequisites

- Java 21 though may work with earlier versions
- Redis

## Run

```bash
./gradlew bootRun
```
## Build and run as a jar
```bash
./gradlew build
java -jar build/libs/search-0.0.1-SNAPSHOT.jar
```

A Tomcat web server is running at http://localhost:8080

## Code

There are two search use cases demonstrated here. The sample data files are in the [resources](src/main/java/resources) folder.

1. See [SearchController.java](src/main/java/sample/SearchController.java) for the code to load and index data, and to execute search queries for a banking transaction use case
It generates fake data except for the description which is pulled from a  file
To load data use this endpoint: http://localhost:8080/load
To search data use this endpoint: http://localhost:8080/search?amount=n&term=t

2. See [SecurityController.java](src/main/java/sample/SecurityController.java) for the code for a securities search use case.
This provides two RESTful services: one to load and index data, and the other to search data. 
To load data use this endpoint: http://localhost:8080/loadsec
To search data use this endpoint: http://localhost:8080/searchsec?term=t where t is a search term.