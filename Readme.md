# Resilience4J Hands On Workshop 
this repo is intended to clone and it provides a step by step guide to cover following topics:

- Eureka Service Discovery
- Config Server
- Spring Cloud Gateway
- Microservice Template to jumpstart all needed microservices

## Distributed Tracing

- Spring Boot Actuators
- Micrometer (former Sleuth)
- Zipkin

## Resilience4J

- Circuit Breaker
- Bulkhead
- Retry
- Rate Limiter

## App Monitoring 

- Actuator Integration with Prometheus
- Grafana Dashboards (Standard metrics and resilience4j metrics)

## Prerequisites

- Mac, Linux or Windows 
- Java JDK 17 or 21
- Maven 3.9.x
- Docker Desktop
- IntelliJ or VS-Code
- optional ab for stress testing

# what we want to build

![Resilience Demo](images/resilience-demo.png)

### <u>Step 1 to Step 4 are preparation steps to setup the environment.</u>

***

## Step 1) Clone the GIT Repo
we need to clone the resilience-handson git repo:
```bash
git clone https://github.com/andrlange/spring-resilience-handson.git
```
the repo is public and don't need any authentication

***

## Step 2) Start the Service Discovery
Start the eureka server by changing to the eureka folder and run:
```bash
mvn spring-boot:run
```
You should be able to open the UI of Eureka http://localhost:8761

Username: eureka Password: password
![Eureka Server](images/eureka-plain.png)

***

## Step 3) Config and Start the Config Server
**Configure the Spring Cloud Config Server**

The spring cloud config server will serve the microservice configurations from a central repo. 
In our case we will use the local file system to serve as git repo.

Change into the "config" folder in config-server and init the folder as local git-repo as followed:

```bash
git init
git add .
git commit -m "first commit"
git branch -M main
```

The application.properties in "config-server/src/main/java/resources" keeps the configuration of the Spring Cloud 
Config Server.

```config
spring.cloud.config.server.git.uri=file://${user.home}/YOUR_FOLDER_PATH/resilience-handson/config-server/config/
```

points to the folder from where our configs are served. This config works for Mac and Linux.
On Windows, you need an extra "/" in the file URL if it is absolute with a drive prefix (for example,file:///${user.home}/config-repo).
```config
spring.cloud.config.server.git.uri=file:///${user.home}/YOUR_FOLDER_PATH/resilience-handson/config-server/config/
```

now you should be able to run the Spring Cloud Config Server from the config-server folder:
```bash
mvn spring-boot:run
```

Info: We are using profiles on the config server, so we can use different configs by switching the profile. In this 
case we just need to add the profile to the config name like: my-service-dev.properties.

Now you should be able to crawl the microservice configs e.g. using a browser, postman or curl:
http://localhost:8888/spring-cloud-gateway/dev

You should now receive the following config:

![Config](images/config-example.png)

***

## Step 4) Run Containers such as DB, Zipkin etc.
Run PostgreSQL, PG-Admin, Zipkin Server, Prometheus and Granafa

To run all the services you can just use the docker-compose.yaml file to start all services.

```bash
docker compose up -d
docker ps
```

now you should see the following running containers:

![Containers](images/container-ps.png)

make sure all containers are up, so you can access all the services later.

***

## Step 5) Resilience4J - Retry

First we want to extend our first microservice "FlakyService" to register with the Service Discovery (Eureka) and 
also supporting distributed tracing using zipkin.

### Step 5.a) Endpoint Test - Flaky Service

Run the Flaky Service 

```bash
mvn spring-boot:run
```

Flaky Service has two endpoints:
- http://localhost:8085/flaky/all returns all courses constantly
- http://localhost:8085/flaky/code/{CODE} returns a course where 50% of the calls returning a 500 Internal Server Error

Later we want to let the calling service retry on the flaky endpoint.

"/all" Endpoint
![Flaky List](images/flaky-all.png)

"/code/BIO" Endpoint should produce two results by random 50%

returning 200 OK
![Flaky OK](images/flaky-ok.png)
returning 500 Error
![Flaky Error](images/flaky-error.png)

### Step 5.b) Adding Service Discovery - Flaky Service

Now we also want to let this service register to the Service Discovery:

Adding Eureka-Dependencies to register and to retrieve services 
```XML
<!-- Eureka Service Discovery -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```
and the configuration to applications.properties
```properties
# Eureka
eureka.client.service-url.defaultZone=http://eureka:password@localhost:8761/eureka/
# here we will add its own shutdown handler to prevent exceptions on stopping the service
eureka.client.should-unregister-on-shutdown=false
eureka.instance.prefer-ip-address=true
```

Hint: we always need to use the application name in the config to let the app register with its name:
```properties
spring.application.name=flaky-service
```
now after restarting the service you will see its registration to eureka:
![Eureka Flaky](images/eureka-flaky.png)

To prevent exceptions on stopping our service due to the shutdown and discovery communication we add our own 
shutdown handler to signal the shutdown.

Add the following class to service: EurekaShutdownHandler.java
```java
import com.netflix.discovery.EurekaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

@Component
@Slf4j

public class EurekaShutdownHandler implements ApplicationListener<ContextClosedEvent> {

    private final EurekaClient eurekaClient;

    public EurekaShutdownHandler(EurekaClient eurekaClient) {
        this.eurekaClient = eurekaClient;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        try {
            // Inform Eureka server about the shutdown
            eurekaClient.shutdown();
        } catch (Exception e) {
            // Log the exception during Eureka deregistration
            log.error("Failed to deregister with Eureka: " + e.getMessage());
        }
    }
}
```
This prevents throwing exceptions on shutdown.

### Step 5.b) Adding Actuators - Flaky Service

we are now adding Actuators and for testing we expose all endpoints:

Adding dependencies:
```XML
<!-- Actuators to expose all operational information --> 
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

we now need to configure what is visible and exposed in the application.properties
```properties
#Actuators
management.endpoint.info.enabled=true
management.info.java.enabled = true
management.info.os.enabled = true
management.endpoints.web.exposure.include=*
management.endpoint.health.group.custom.show-components=always
management.endpoint.health.group.custom.show-details=always
management.endpoint.health.show-details=always
# we need this property to use this service demonstrating rate limiters
management.health.ratelimiters.enabled=true
```

after we restart the service we can call the actuator endpoints:
- http://localhost:8085/actuator retrieving all exposed endpoints
- http://localhost:8085/actuator/health retrieving health infos 

![Flaky Actuators](images/flaky-actuators.png)
here you also can see the retrieved services list of the Service Discovery [eureka]

### Step 5.c) Adding distributed tracing capabilities - Flaky Service

When we later will take a look to distributed tracing wen need to add tracing metadata to all http headers so the 
api calls and responses can be traced. We want to add a Zipkin-Header providing a traceId and a spanId to trace 
down whole chains of calls using "Micrometer Tracing Bridge" as part of the new extended observability capabilities 
of Spring Boot 3.x.

adding the dependencies:
```XML
<!-- For Micrometer tracing support with Zipkin-Brave -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- to send Zipkin data to a remote Zipkin instance -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

adding the configuration to the application.properties:

```properties
# IO Micrometer Tracing (Migrating from Sleuth to Micrometer)

# traceID and spanId are predefined MDC keys - we want the logs to include them
logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]
# All traces should be sent to latency analysis tool
management.tracing.sampling.probability=1.0
# For Exemplars to work we need histogram buckets
management.metrics.distribution.percentiles-histogram.http.server.requests=true
# here we forward tracing data to the zipkin service endpoint
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
```
together with other containers in our docker-compose.yaml we started already a zipkin server to retrieve tracing 
data.

Now after restart you should be able to call and trace the endpoints:
- http://localhost:8085/flaky/all
and see the new generated traceId and spanId
![Flaky Tracing](images/flaky-traceid.png)

We will repeat this integration for all other services, so we can trace the whole calling chain in zipkin.
you also should see the tracing information in zipkin:
![Flaky Zipkin](images/flaky-zipkin.png)

### Step 5.d) Adding metrics for Prometheus / Grafana - Flaky Service

As final preparing we expose metrics to a prometheus actuator endpoint so prometheus can crawl this endpoint to 
gather operational metrics. We will also see how Resilience4J metrics will be part of those metrics.

Exposing prometheus-actuator endpoint by adding the dependency:
```XML
<!-- For Micrometer to produce metrics for prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

after restarting a new actuator endpoint for Prometheus is exposed:
- http://localhost:8085/actuator/prometheus
![Prometheus Actuator](images/prometheus-actuator.png)

We also started a Prometheus and Grafana Service using our docker-compose.yaml, so we have an Prometheus and Grafana 
Service running already.
check prometheus target status to see if prometheus can reach our flaky service:
- http://localhost:9090/targets
![Prometheus Flaky](images/prometheus-flaky.png)

We should see two green/active status
- Prometheus itself
- Flaky-Service

Hint!: In the case flaky-service is not reachable from prometheus, it might be that it need an additional 
configuration of the prometheus container (for Windows and Ubuntu). The prometheus container needs to access the 
host ports outside its container network to "see" the local running microservice instances. 

extra_hosts configuration 


```YAML
  prometheus-service:
    image: bitnami/prometheus
    container_name: prometheus-service
    restart: unless-stopped
    ports:
      - "9090:9090"
    volumes:
      - './vols/prometheus_service:/opt/bitnami/prometheus/data'
      - './prometheus.yaml:/opt/bitnami/prometheus/conf/prometheus.yml'
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

### Connecting Prometheus Datasource
Grafana needs to connect to Prometheus to collect all data and let them be used by the dashboards.

Grafana UI:
- http://localhost:3000 Username:admin Password:password

Steps:
- goto: Home/Connections/Data Sources > add new Data Source
- select Prometheus
- you only need to provide its url: http://prometheus-service:9090
  - in this case we are using the internal container network to resolve the container network ip using its service name
- save & test the connection

![Grafana Connection](images/grafana-connection.png)

Importing our first Spring Boot Dashboard:

Steps:
- goto: Home/Dashboards/new > import
- select the dashboard: dashboards/springboot.json
- select the new created datasource: prometheus
- and import

You should see a Spring Boot Dashboard showing metrics from flaky-service (Instance:host.docker.internal:8085)
![Grafana Flaky](images/grafana-flaky.png)
