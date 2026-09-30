# Heidi Coordinator Backend

This service is part of the Heidi platform and provides coordinator APIs for
wallet-facing credential flows. The app design, UX and implementation were
developed by [Ubique Innovation AG](https://www.ubique.ch).

## Services

This service provides endpoints for initializing and starting credential issuance processes, managing connections and
handling callbacks for connection establishment.

## Usage

It is recommended to interact with this service through an OID4VCI-compatible wallet
or another client of the coordinator APIs.

To run the service locally, ensure you have up-to-date installations of Maven, Java version 21 and Docker. Then, one can
run the following from the repository root:

```
docker compose up -d platform-postgres
mvn clean install -f heidi-platform-api/pom.xml
mvn clean -f heidi-platform-api/heidi-platform-api-ws/pom.xml spring-boot:run -Dspring-boot.run.profiles=local,no-security
```

### OpenAPI Docs

Given a locally running instance of the service, one can inspect the API documentation
at [http://localhost:8081/api-docs/swagger-ui/index.html](http://localhost:8081/api-docs/swagger-ui/index.html).
