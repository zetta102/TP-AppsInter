package tp.appsinter.cine.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Sort whitelist")
class SortWhitelistTest {

    private final SortWhitelist whitelist = SortWhitelist.of("productName").alias("price", "cost");

    @Test
    @DisplayName("leaves an unsorted page untouched")
    void keepsUnsortedPage() {
        Pageable pageable = PageRequest.of(2, 10);

        assertThat(whitelist.apply(pageable)).isSameAs(pageable);
    }

    @Test
    @DisplayName("translates aliases and keeps direction and paging")
    void translatesAliases() {
        Pageable result = whitelist.apply(PageRequest.of(1, 5, Sort.by(Sort.Order.desc("price"),
                Sort.Order.asc("productName"))));

        assertThat(result.getPageNumber()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(5);
        assertThat(result.getSort()).containsExactly(Sort.Order.desc("cost"), Sort.Order.asc("productName"));
    }

    @Test
    @DisplayName("remembers the order with the published names, for the response")
    void remembersPublishedSort() {
        Pageable result = whitelist.apply(PageRequest.of(0, 5, Sort.by(Sort.Order.desc("price"))));

        assertThat(result).isInstanceOfSatisfying(WhitelistedPageRequest.class,
                page -> assertThat(page.getPublishedSort()).containsExactly(Sort.Order.desc("price")));
    }

    @Test
    @DisplayName("publishes its property names in declaration order")
    void listsPublishedProperties() {
        assertThat(whitelist.publishedProperties()).containsExactly("productName", "price");
    }

    @Test
    @DisplayName("rejects a property that is not published, even if the entity has it")
    void rejectsUnpublishedProperty() {
        assertThatThrownBy(() -> whitelist.apply(PageRequest.of(0, 5, Sort.by("cost"))))
                .isInstanceOfSatisfying(SortWhitelist.InvalidSortPropertyException.class,
                        ex -> assertThat(ex.getProperty()).isEqualTo("cost"));
    }
}
