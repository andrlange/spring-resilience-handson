package cool.cfapps.addressservice.controller;

import cool.cfapps.addressservice.dto.AddressResponse;
import cool.cfapps.addressservice.service.AddressService;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<AddressResponse> getAddressById(@PathVariable Long id) {
        log.info("get address with id: {}", id);
        Optional<AddressResponse> result = addressService.findById(id);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
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
