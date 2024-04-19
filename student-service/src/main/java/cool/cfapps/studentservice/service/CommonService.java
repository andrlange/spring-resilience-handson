package cool.cfapps.studentservice.service;

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

    public CommonService(AddressFeignClient addressFeignClient) {
        this.addressFeignClient = addressFeignClient;
    }

    public Optional<AddressResponse> getStudentAddress(Long addressId) {
        AddressResponse response = addressFeignClient.getAddressById(addressId);
        return Optional.of(response);
    }

    public Optional<AddressResponse> getStudentAddressNoLimit(Long addressId) {
        AddressResponse response = addressFeignClient.getAddressByIdNoLimit(addressId);
        return Optional.of(response);
    }

}
