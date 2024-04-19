package cool.cfapps.studentservice.service;

import cool.cfapps.studentservice.client.AddressFeignClient;
import cool.cfapps.studentservice.dto.FlakyDto;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class FlakyService {

    private final AddressFeignClient addressFeignClient;

    public FlakyService(AddressFeignClient addressFeignClient) {
        this.addressFeignClient = addressFeignClient;
    }


    public Optional<FlakyDto> getFlakyByCode(String code) {
        FlakyDto dto = addressFeignClient.getFlakyByCode(code);
        return Optional.of(dto);
    }

    public List<FlakyDto> getAllFlaky() {
        return addressFeignClient.getAllFlaky();
    }
}
