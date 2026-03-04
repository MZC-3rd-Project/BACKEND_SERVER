package java.com.example.store.repository;

import com.example.store.entity.StoreStatus;
import com.example.store.entity.Stores;
import com.example.store.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

@DataJpaTest
@ActiveProfiles("test")
public class StoreRepositoryTest {
    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private StoreAddressRepository storeAddressRepository;

    @Autowired
    private StoreContactRepository storeContactRepository;

    @Autowired
    private StoreProfileRepository storeProfileRepository;

    @Autowired
    private StoreImageRepository storeImageRepository;

    private Stores savedStore;

    @BeforeEach
    void setUp() {
        savedStore = storesRepository.save(
            Stores.create(1001L, "테스트 가게")
        );
    }

    @Test
    @DisplayName("가게 저장 및 조회")
    void saveAndFindStore() {
        Optional<Stores> found = storesRepository.findById(savedStore.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStoreName()).isEqualTo("테스트 가게");
        assertThat(found.get().getUserId()).isEqualTo(1001L);
        assertThat(found.get().getStatus()).isEqualTo(StoreStatus.INACTIVE);
    }

}
