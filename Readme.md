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

## Step 1) Clone the GIT Repo
we need to clone the resilience-handson git repo:
```bash
git clone https://github.com/andrlange/spring-resilience-handson.git
```
the repo is public and don't need any authentication

## Step 2) Start the Service Discovery
Start the eureka server by changing to the eureka folder and run:
```bash
mvn spring-boot:run
```
You should be able to open the UI of Eureka http://localhost:8761

Username: eureka Password: password
![Eureka Server](images/eureka-plain.png)


## Step 3) Config and Start the Config Server
<b>Configure the Spring Cloud Config Server</b>

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

## Step 5) Resilience4J - Retry

First we want to extend our first microservice "FlakyService" to register with the Service Registry (Eureka) and 
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

/all Endpoint
![Flaky List](images/flaky-all.png)

/code/BIO Endpoint should produce two results by random 50%

returning 200 OK
![Flaky OK](images/flaky-ok.png)
returning 500 Error
![Flaky Error](images/flaky-error.png)

Now we also want to let this service register to the Service Registry:

