package com.utochkin.shopservice.services;

import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.dto.ProductDtoRequest;
import com.utochkin.shopservice.exceptions.ProductNotFoundException;
import com.utochkin.shopservice.mappers.ProductMapper;
import com.utochkin.shopservice.models.Product;
import com.utochkin.shopservice.repositories.ProductRepository;
import com.utochkin.shopservice.requests.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepo;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    private UUID uuid;
    private Product product;
    private OrderRequest orderRequest;

    @BeforeEach
    void setUp() {
        uuid = UUID.randomUUID();
        product = Product.builder()
                .articleId(uuid)
                .name("Item")
                .quantity(5)
                .price(10.0)
                .build();
        orderRequest = new OrderRequest(uuid, 3);
    }

    @Test
    @DisplayName("checkOrder: возвращает значение true, если все количества достаточны")
    void checkOrder_allAvailable() {
        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of(product));

        boolean ok = productService.checkOrder(List.of(orderRequest));

        assertThat(ok).isTrue();
        verify(productRepo).findAllByArticleIds(List.of(uuid));
    }

    @Test
    @DisplayName("checkOrder: озвращает значение false, если количество недостаточно или отсутствует")
    void checkOrder_insufficientOrMissing_returnsFalse() {
        product.setQuantity(2);
        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of(product));
        assertThat(productService.checkOrder(List.of(orderRequest))).isFalse();

        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of());
        assertThat(productService.checkOrder(List.of(orderRequest))).isFalse();
    }

    @Test
    @DisplayName("getSumTotalPriceOrder: возвращает правильную округленную сумму")
    void getSumTotalPriceOrder_happy() {
        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of(product));
        double sum = productService.getSumTotalPriceOrder(List.of(orderRequest));
        assertThat(sum).isEqualTo(30.0);
    }

    @Test
    @DisplayName("getSumTotalPriceOrder: выбрасывает исключение, когда товар не найден")
    void getSumTotalPriceOrder_missing_throws() {
        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> productService.getSumTotalPriceOrder(List.of(orderRequest)))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Продукт с articleId");
    }

    @Test
    @DisplayName("changeTotalQuantityProductsAfterCreateOrder: уменьшает количество")
    void changeTotalQuantityProductsAfterCreateOrder_happy() {
        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of(product));

        productService.changeTotalQuantityProductsAfterCreateOrder(List.of(orderRequest));

        assertThat(product.getQuantity()).isEqualTo(2);
        verify(productRepo).saveAll(List.of(product));
    }

    @Test
    @DisplayName("changeTotalQuantityProductsAfterCreateOrder: выбрасывает исключение при отсутствии или недостаточном количестве")
    void changeTotalQuantityProductsAfterCreateOrder_errors() {
        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of());
        assertThatThrownBy(() -> productService.changeTotalQuantityProductsAfterCreateOrder(List.of(orderRequest)))
                .isInstanceOf(ProductNotFoundException.class);

        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of(product));
        product.setQuantity(1);
        assertThatThrownBy(() -> productService.changeTotalQuantityProductsAfterCreateOrder(List.of(orderRequest)))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Недостаточно товара");
    }

    @Test
    @DisplayName("changeTotalQuantityProductsAfterRefundedOrder: восстанавливает количество")
    void changeTotalQuantityProductsAfterRefundedOrder_happy() {
        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of(product));

        productService.changeTotalQuantityProductsAfterRefundedOrder(List.of(orderRequest));

        assertThat(product.getQuantity()).isEqualTo(8);
        verify(productRepo).saveAll(List.of(product));
    }

    @Test
    @DisplayName("changeTotalQuantityProductsAfterRefundedOrder: выбрасывает исключение, когда пусто")
    void changeTotalQuantityProductsAfterRefundedOrder_missing_throws() {
        when(productRepo.findAllByArticleIds(List.of(uuid)))
                .thenReturn(List.of());
        assertThatThrownBy(() -> productService.changeTotalQuantityProductsAfterRefundedOrder(List.of(orderRequest)))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("getAllProducts")
    void getAllProducts_happy() {
        Pageable pg = PageRequest.of(0,2);
        Product p2 = Product.builder().articleId(UUID.randomUUID())
                .name("X").quantity(1).price(5.0).build();
        when(productRepo.findAll(pg))
                .thenReturn(new PageImpl<>(List.of(product, p2)));
        ProductDto dto1 = new ProductDto(uuid, "Item", 5, 10.0);
        ProductDto dto2 = new ProductDto(p2.getArticleId(), "X",1,5.0);
        when(productMapper.toListDto(List.of(product, p2))).thenReturn(List.of(dto1, dto2));

        List<ProductDto> out = productService.getAllProducts(pg);
        assertThat(out).containsExactly(dto1, dto2);
    }

    @Test
    @DisplayName("getProduct")
    void getProduct_cases() {
        ProductDto dto = new ProductDto(uuid, "Item", 5, 10.0);
        when(productRepo.findByArticleId(uuid)).thenReturn(Optional.of(product));
        when(productMapper.toDto(product)).thenReturn(dto);
        assertThat(productService.getProduct(uuid)).isEqualTo(dto);

        when(productRepo.findByArticleId(uuid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> productService.getProduct(uuid))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("addProduct")
    void addProduct_happy() {
        ProductDtoRequest req = new ProductDtoRequest("New", 4, 20.0);

        Product ent = new Product(); ent.setArticleId(UUID.randomUUID());
        when(productMapper.toEntity(any())).thenReturn(ent);

        when(productRepo.save(ent)).thenReturn(ent);
        ProductDto outDto = new ProductDto(ent.getArticleId(),"New",4,20.0);
        when(productMapper.toDto(ent)).thenReturn(outDto);

        ProductDto res = productService.addProduct(req);
        assertThat(res).isEqualTo(outDto);
        verify(productRepo).save(ent);
    }

    @Test
    @DisplayName("deleteProduct")
    void deleteProduct_cases() {
        when(productRepo.findByArticleId(uuid)).thenReturn(Optional.of(product));
        productService.deleteProduct(uuid);
        verify(productRepo).deleteByArticleId(uuid);

        when(productRepo.findByArticleId(uuid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> productService.deleteProduct(uuid))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("updateProduct")
    void updateProduct_cases() {
        ProductDtoRequest req = new ProductDtoRequest("Upd", 2, 15.0);
        when(productRepo.findByArticleId(uuid)).thenReturn(Optional.of(product));
        when(productRepo.save(product)).thenReturn(product);
        ProductDto outDto = new ProductDto(uuid, "Upd", 2, 15.0);
        when(productMapper.toDto(product)).thenReturn(outDto);

        ProductDto res = productService.updateProduct(uuid, req);
        assertThat(res).isEqualTo(outDto);
        assertThat(product.getName()).isEqualTo("Upd");
        assertThat(product.getQuantity()).isEqualTo(2);
        assertThat(product.getPrice()).isEqualTo(15.0);

        when(productRepo.findByArticleId(uuid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> productService.updateProduct(uuid, req))
                .isInstanceOf(ProductNotFoundException.class);
    }

}

