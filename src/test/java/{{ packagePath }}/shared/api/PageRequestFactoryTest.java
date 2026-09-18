package {{ package }}.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import {{ package }}.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageRequestFactoryTest {

    private final PageRequestFactory factory = new PageRequestFactory();

    @Test
    void appliesContractDefaults() {
        PageRequest request = factory.create(null, null, null);

        assertThat(request.getPageNumber()).isZero();
        assertThat(request.getPageSize()).isEqualTo(20);
        assertThat(request.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void clampsOversizedPageSizeAndNegativePage() {
        PageRequest request = factory.create(-5, 1000, "username,asc");

        assertThat(request.getPageNumber()).isZero();
        assertThat(request.getPageSize()).isEqualTo(100);
        assertThat(request.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "username"));
    }

    @Test
    void acceptsWhitelistedSortFieldsOnly() {
        assertThatThrownBy(() -> factory.create(0, 20, "password_hash,asc"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Sort field is not allowed: password_hash");
    }

    @Test
    void rejectsInvalidSortDirection() {
        assertThatThrownBy(() -> factory.create(0, 20, "createdAt,sideways"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Sort direction must be asc or desc");
    }
}
