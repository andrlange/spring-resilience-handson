# Resilience4J Hands-On Workshop 


#### Powered by: Spring Boot 3.2.5 and Spring Cloud 2023.0.1

### this repo is intended to clone, and it provides a step-by-step guide to cover following topics:

- Eureka Service Discovery
- Config Server
- Spring Cloud Gateway
- Basic microservice to jumpstart all needed microservices

## Distributed Tracing

- Spring Boot Actuators
- Micrometer (former Sleuth)
- Zipkin

## Resilience4J

- Circuit Breaker
- Bulkhead
- Retry
- Rate Limiter
- Time Limiter

## App Monitoring 

- Actuator Integration with Prometheus
- Grafana Dashboards (Standard metrics and resilience4j metrics)

## Prerequisites

- Mac, Linux or Windows 
- Java JDK 17 or 21
- Maven 3.9.x
- Docker Desktop
- IntelliJ or VS-Code
- Git Client
- optional ab for stress testing

# what we want to build

![Resilience Demo](images/resilience-demo.png)

### <u>Step 1 to Step 4 are preparation steps to setup the environment.</u>

***

## Step 1: Clone the GIT Repo
we need to clone the resilience-handson git repo:
```bash
git clone https://github.com/andrlange/spring-resilience-handson.git
```
the repo is public and don't need any authentication

***

## Step 2: Start the Service Discovery
Start the eureka server by changing to the eureka folder and run:
```bash
mvn spring-boot:run
```
You should be able to open the UI of Eureka http://localhost:8761

Username: eureka Password: password
![Eureka Server](images/eureka-plain.png)

***

## Step 3: Config and Start the Config Server
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

## Step 4: Run Containers such as DB, Zipkin etc.
Run PostgreSQL, PG-Admin, Zipkin Server, Prometheus and Grafana

To run all the services you can just use the docker-compose.yaml file to start all services.

```bash
docker compose up -d
docker ps
```

now you should see the following running containers:

![Containers](images/container-ps.png)

make sure all containers are up, so you can access all the services later.

***

## Step 5: First Microservice and Monitoring Setup

First we want to extend our first microservice "FlakyService" to register with the Service Discovery (Eureka) and 
also supporting distributed tracing using zipkin.

### Step 5.a: Endpoint Test - Flaky Service

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

***

### Step 5.b: Adding Service Discovery - Flaky Service

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

***

### Step 5.c: Adding Actuators - Flaky Service

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

***

### Step 5.d: Adding distributed tracing capabilities - Flaky Service

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
```
```XML
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

***

### Step 5.e: Adding metrics for Prometheus / Grafana - Flaky Service

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

We also started a Prometheus and Grafana Service using our docker-compose.yaml, so we have Prometheus and Grafana 
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

***

## Step 6: Resilience4J - Retry

### Prepare using Resilience4J and Config-Server
Before we start into resilience patterns we need to add it to a microservice calling one other.

Student-Service (calls)-> Flaky Service
we know that Flaky Service will produce 500 Errors by 50%, in this case we need to tell Student Service how often it 
should retry to call the /code/{CODE} endpoint of Flaky Service

Hint!: we will use the OpenFeign Client to configure a very efficient way of creating a HTTP-Client by defining only 
it's Interface.

Student Service will retrieve its application.properties from a central Config Server we already started earlier in 
Step 3. Instead of the application.properties Student Service only needs to have a bootstrap.properties file.

```properties
spring.application.name=student-service

spring.cloud.config.discovery.service-id=config-server
spring.cloud.config.profile=dev
```

***

### Step 6.a: Running Spring Cloud Gateway 

Now we will start Spring Cloud Gateway, so we are able to offload Load-Balancing in a central point for our 
microservices and show how distributed tracing will take effect.

Run Spring Cloud Gateway
```bash
mvn spring-boot:run
```

***

### Step 6.b: Running Student Service

First we will start student service to check distributed tracing:
```bash
mvn spring-boot:run
```

Hint!: Student Service connects to PostgreSQL and creates two entries into the students table on startup.

Check to call the following endpoint:
http://localhost:9000/student-service/api/v1/flaky

You should again receive all student courses:
Client -> Gateway -> Student Service -> Gateway -> Flaky Service

Checking Zipkin to see the distributed trace of this call:
![Zipkin Distributed One](images/zipkin-trace-dist-one.png)

***

### Step 6.c: Implementing Resilience4J:Retry

Adding dependencies to Student Service pom.xml
```xml
<!-- adding Resilience4J Circuit Breaker and Dependencies -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```
```xml
<!-- adding Resilience4J bulkhead, retry, rate limiter, timout -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-all</artifactId>
    <version>2.1.0</version>
</dependency>
```

Configure retry in application.properties
```properties
# Adding configuration for Resilience4J:Retry
# Max 3 Attempts
resilience4j.retry.instances.flakyRetry.maxAttempts=3
# Wait between retries
resilience4j.retry.instances.flakyRetry.waitDuration=200
# Retry on this Exception Type
resilience4j.retry.instances.flakyRetry.retryExceptions=java.lang.RuntimeException
```

We will use the flaky endpoint and Flaky Service to test the Retry.

We need to add some code to the RetryService to track the retries and throw exceptions when we want the caller to 
retry as configured:

```JAVA
import cool.cfapps.studentservice.client.AddressFeignClient;
import cool.cfapps.studentservice.dto.FlakyDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import io.github.resilience4j.retry.annotation.Retry;


import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class FlakyService {

    // The total number of calls to the /api/v1/retry/code/{code} endpoint
    private int callCnt = 1;
    // the total number of retries, reset to 0 after each call
    private int retryCnt = 0;

    private final AddressFeignClient addressFeignClient;

    public FlakyService(AddressFeignClient addressFeignClient) {
        this.addressFeignClient = addressFeignClient;
    }


    @Retry(name = "flakyRetry")
    public Optional<FlakyDto> getFlakyByCode(String code) {
        retryCnt++;
        log.info("getFlakyByCode: {} : call:retry = {}:{}", code, callCnt, retryCnt);
        FlakyDto dto = addressFeignClient.getFlakyByCode(code);
        // If we don't receive a response from the server, throw a runtime exception to retry
        if (dto == null) {
            throw new RuntimeException();
        }
        callCnt++;
        retryCnt = 0;
        return Optional.of(dto);
    }

    public List<FlakyDto> getAllFlaky() {
        return addressFeignClient.getAllFlaky();
    }
}
```

Calling the endpoint
- http://localhost:9000/student-service/api/v1/flaky/code/BIO

several times creates random responses on the amount of retires. In average the Flaky Service produces 50% of 
  failures (HTTP:500).

It can happen also, that Flaky Service fails 4 times in a row, so the Retry will break and come up with a final error.

Retry example 3 attempts:
![Retry OK](images/retry-ok-3.png)
we can see three retries logged in the Student Service log output

Result:
![Retry OK Result](images/retry-ok-result.png)

Using Zipkin we can see all 3 attempts and trace down the overall response time
![Retry OK Zipkin](images/retry-zipkin-ok.png)


Failed Attempt: In this example 4 Flaky Service calls in row occurred, so the retry stopped after 3 attempts.
Retry example failure:
![Retry Fail](images/retry-fail-log.png)
we can see three retries and failing logged in the Student Service log output

Result:
![Retry Fail Result](images/retry-fail-result.png)

Using Zipkin we can see all attempts and trace down the overall response time until HTTP:500 shows up
![Retry Fail Zipkin](images/retry-fail-zipkin.png)

***

## Step 7: Resilience4J - Rate Limiter

We will use the last microservice to demonstrate rate limiting. 
The Address Service already has all dependencies, so we just need to add some code and configure Rate Limiter.

Let's add rate limiting config to the Address Service on the Config Server:
```properties
# Adding configuration for Resilience4J:Rate Limiter
resilience4j.ratelimiter.instances.addressLimit.limitForPeriod=5
resilience4j.ratelimiter.instances.addressLimit.limitRefreshPeriod=15s
resilience4j.ratelimiter.instances.addressLimit.timeoutDuration=5s

resilience4j.ratelimiter.instances.addressLimit.registerHealthIndicator=true
resilience4j.ratelimiter.instances.addressLimit.eventConsumerBufferSize=100
```

now we will add Rate Limiter to the AddressController
```JAVA
import cool.cfapps.addressservice.dto.AddressResponse;
import cool.cfapps.addressservice.service.AddressService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/address")
@Slf4j
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping()
    public List<AddressResponse> getAllAddresses() {
        return addressService.findAll();
    }

    @GetMapping("/{id}")
    @Observed(
            name = "user.name",
            contextualName = "address-->database",
            lowCardinalityKeyValues = {"userType", "userType2"}
    )
    @RateLimiter(name = "addressLimit", fallbackMethod = "addressErrorResponse")
    public ResponseEntity<AddressResponse> getAddressById(@PathVariable Long id) {
        log.info("get address with id: {}", id);
        Optional<AddressResponse> result = addressService.findById(id);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    public ResponseEntity<AddressResponse> addressErrorResponse(@PathVariable Long id, Throwable th) {
        log.info("fallback on rate limit hit");

        Optional<AddressResponse> result = Optional.of(AddressResponse.builder()
                .failure(true)
                .build());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result.get());
    }

    @GetMapping("/nolimit/{id}")
    @Observed(
            name = "user.name",
            contextualName = "address-->database",
            lowCardinalityKeyValues = {"userType", "userType2"}
    )
    public ResponseEntity<AddressResponse> getUnlimitedAddressById(@PathVariable Long id) {
        log.info("get address with no limit id: {}", id);
        Optional<AddressResponse> result = addressService.findById(id);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

}
```

Now we can restart the Address Service, on starting it, it will create two Address Entries into the addresses table.
- http://localhost:9000/address-service/api/v1/address/1

If we now call the same API endpoint more than 5 times in less than 15 seconds, the Rate Limiter will stall the 
response for the configured time (5s) and return in our case a NULL Object.

The Address Service log shows the call sequence and the limiting:
![Rate Limiter Log](images/rate-limiter-log.png)

The response will provide a HTTP:429 Error and a Fall-Back Object (NULL)
![Rate Limiter Response](images/rate-limiter-response.png)

***

## Step 8: Resilience4J - Bulkhead

Prepare to simulate a slow backend service, we will add some time (500ms) to the Student Service - getAllStudents().
We can uncomment the following line:
```text
try{Thread.sleep(500);} catch (InterruptedException e) {}
```

To configure Bulkhead we add the following properties to the Student Service config:
```properties
# Adding configuration for Resilience4J:Bulkhead
resilience4j.bulkhead.instances.bulkheadWithConcurrentCalls.maxConcurrentCalls=3
resilience4j.bulkhead.instances.bulkheadWithConcurrentCalls.writableStackTraceEnabled=no
```
we will only allow max 3 concurrent calls to protect the slow backend and prevent the caller to fail.

Now we need to add @Bulkhead annotation to the StudentController:
```JAVA
import cool.cfapps.studentservice.dto.StudentResponse;
import cool.cfapps.studentservice.service.StudentService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student")
@Slf4j
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/{id}")
    @Observed(
            name = "user.name",
            contextualName = "student-->address",
            lowCardinalityKeyValues = {"userType", "userType2"}
    )
    public ResponseEntity<StudentResponse> getStudentById(@PathVariable Long id) {
        log.info("getStudentById: {}", id);
        return studentService.getStudent(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }



    @GetMapping
    @Bulkhead(name = "bulkheadWithConcurrentCalls")
    public List<StudentResponse> getAllStudents() {
        log.info("getAllStudents()");
        return  studentService.getAllStudents();
    }

}
```

In this example we are using Apache ab to create 5 concurrent calls:
- http://localhost:9000/student-service/api/v1/student

![Bulkhead Call AB](images/bulkhead-call.png)

The Student Service log:
![Bulkhead Call AB](images/bulkhead-log.png)

***

## Step 9: Resilience4J - Circuit Breaker

Implementing Circuit Breaker uses Spring AOP to intercept requests. Using Spring AOP needs to separate our service 
functions into a separate Class (CommonService).

First let us add Circuit Breaker config to Studend Service properties:
```properties
# Adding configuration for Resilience4J:Circuit Breaker
resilience4j.circuitbreaker.instances.addressService.sliding-window-size=10
resilience4j.circuitbreaker.instances.addressService.failureRateThreshold=50
resilience4j.circuitbreaker.instances.addressService.waitDurationInOpenState=10s
resilience4j.circuitbreaker.instances.addressService.automatic-transition-from-open-to-half-open-enabled=true
resilience4j.circuitbreaker.instances.addressService.permitted-number-of-calls-in-half-open-state=5

resilience4j.circuitbreaker.instances.addressService.allow-health-indicator-to-fail=true
resilience4j.circuitbreaker.instances.addressService.register-health-indicator=true
```

Scenario: From CLOSE : 10 Calls, if 50% fails we want to OPEN the Circuit Breaker (we will stop the Address Service), 
after 10s it falls back into HALF_OPEN, when we get positive responses in HALF-OPEN the Circuit Breaker will fall back into CLOSED.

Now we need to add Circuit Breaker to the CommonService (Spring AOP Pattern)
```JAVA
import cool.cfapps.studentservice.client.AddressFeignClient;
import cool.cfapps.studentservice.dto.AddressResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class CommonService {

    private final AddressFeignClient addressFeignClient;
    private int cnt=0;

    public CommonService(AddressFeignClient addressFeignClient) {
        this.addressFeignClient = addressFeignClient;
    }

    @CircuitBreaker(name = "addressService", fallbackMethod = "getStudentAddressFallback")
    public Optional<AddressResponse> getStudentAddress(Long addressId) {
        cnt++;
        log.info("Calling address service cnt: {}", cnt);
        AddressResponse response = addressFeignClient.getAddressById(addressId);
        return Optional.of(response);
    }

    public Optional<AddressResponse> getStudentAddressNoLimit(Long addressId) {
        AddressResponse response = addressFeignClient.getAddressByIdNoLimit(addressId);
        return Optional.of(response);
    }

}
```

Now we can call the endpoints for testing:
- http://localhost:9000/student-service/api/v1/student/0

As long the Address Service is up, and we don't have more than 50% failing calls (10 Calls min.), the Circuit Breaker 
stays CLOSED.

Check the Actuator:
- http://localhost:9000/student-service/actuator/health

![Circuit Breaker Closed](images/circuit-breaker-close.png)

As soon we stop the Address Service and we have 50% failed calls out of ten, the Service Breaker changes to OPEN state.

![Circuit Breaker Closed](images/circuit-breaker-open.png)

After the configured time (10s) the Circuit Breaker will switch to HALF-OPEN to try to recover the CLOSE state. 

![Circuit Breaker Closed](images/circuit-breaker-half.png)


As soon we stay on responsive calls the Circuit Breaker will change again to CLOSED state.


***


## Step 10: Resilience4J - Time Limiter
y
To use TimeLimiter we want to call a very slow API endpoint. The /version endpoint of Flaky Servie will take 5
seconds to return.

- http://localhost:9000/flaky-service/flaky/version

The Student Service will provide access to the FlakyService Version endpoint through:

- http://localhost:9000/student-service/api/v1/flaky/version


The FlakyService in the Student Service will try to call the client, on timeout we want to fall back to an
alternative result. Add both methods to service/FlakyService
```JAVA
@TimeLimiter(name = "flakyVersion", fallbackMethod = "getVersionFlakyTimeout")
    public CompletableFuture<String> getVersionFlaky() {
        return CompletableFuture.supplyAsync(addressFeignClient::getVersionFlaky);
    }

    public CompletableFuture<String> getVersionFlakyTimeout(Throwable th) {
        log.info("getVersionFlaky Fallback");
        return CompletableFuture.completedFuture("Not Available:" + th.getMessage());
    }
```

We want to add a new Version endpoint in our Student Service for Flaky Service,
add this method to controller/FlakyController
```JAVA
@GetMapping("/version")
    public CompletableFuture<String> getVersionFlaky() {
        log.info("getVersionFlaky");
        return flakyService.getVersionFlaky();
    }
```

Let us add the FlakyVersion endpoint to the client/AddressFeignClient
```JAVA
@GetMapping("/flaky-service/flaky/version")
    String getVersionFlaky();
```

After restarting Student Service we can call the given endpoint.

Student Service log
![Timeout Log](images/timeout-log.png)

Calling the Flaky Service Version endpoint
![Timeout Log](images/timeout-call.png)

Student Service response calling the Flaky Service Version endpoint
![Timeout Log](images/timeout-fallback.png)


***


## Step 11: Finally we will add a Resilience4J Dashboard to Grafana

Like Step 5.d we will add a Dashboard. Execute the same steps to import dashboards/resilience.json
Hint!: Just remember to chose prometheus as data source.

Prometheus should collect all configured endpoints, check:
- http://localhost:9090/targets?search=

![Prometheus All](images/prometheus-all.png)


***

Finally, you should be able to track Resilience4J Metrics and visualize it in Grafana:
![Grafana R4J](images/grafana-r4j.png)


***

## Addendum

If you need, you will find all changes for all Services in folder: full-microservices and the configurations as 
config/SERVICE-DEV.full

Don't forget to stop all Services and:
```bash
docker compose down
```

***

### Aspect order
The Resilience4J Aspects order is the following:
```text
Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( Function ) ) ) ) )
```
so Retry is applied at the end (if needed).
If you need a different order, you must use the functional chaining style instead of the Spring annotations style or explicitly set aspect order using the following properties:

```text
- resilience4j.retry.retryAspectOrder
- resilience4j.circuitbreaker.circuitBreakerAspectOrder
- resilience4j.ratelimiter.rateLimiterAspectOrder
- resilience4j.timelimiter.timeLimiterAspectOrder
- resilience4j.bulkhead.bulkheadAspectOrder
```

### ENJOY THIS HANDS-ON




