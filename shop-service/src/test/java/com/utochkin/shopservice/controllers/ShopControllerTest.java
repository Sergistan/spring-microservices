package com.utochkin.shopservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.shopservice.config.AuthServerRoleConverter;
import com.utochkin.shopservice.config.Config;
import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.dto.ProductDtoRequest;
import com.utochkin.shopservice.exceptions.CustomAccessDeniedHandler;
import com.utochkin.shopservice.exceptions.ProductNotFoundException;
import com.utochkin.shopservice.requests.OrderRequest;
import com.utochkin.shopservice.services.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ShopController.class)
@Import({
        Config.class,
        AuthServerRoleConverter.class,
        CustomAccessDeniedHandler.class
})
@DisplayName("ShopController")
@ActiveProfiles("test")
class ShopControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("updateProduct разрешен, когда у нас пользователь Admin")
    void update_ok_withAdminRole() throws Exception {
        UUID id = UUID.randomUUID();
        var req = new ProductDtoRequest("L", 2, 200.0);
        var dto = new ProductDto(id, "L", 2, 200.0);
        given(productService.updateProduct(id, req)).willReturn(dto);

        mvc.perform(put("/shop/api/v1/updateProduct/{id}", id)
                        .with(jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(200.0));
    }

    @Test
    @DisplayName("updateProduct не разрешен, когда у нас пользователь не Admin")
    void update_forbidden_forUserRole() throws Exception {
        mvc.perform(put("/shop/api/v1/updateProduct/{id}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductDtoRequest("X", 1, 10.0))))
                .andExpect(status().isForbidden());
    }

    @Nested
    @DisplayName("проверка /shop/api/v1/checkOrder")
    @WithMockUser(roles = "ADMIN")
    class OrderChecks {
        @Test
        @DisplayName("checkOrder → true")
        void checkOrder_true() throws Exception {
            List<OrderRequest> reqs = List.of(new OrderRequest(UUID.randomUUID(), 2));
            given(productService.checkOrder(reqs)).willReturn(true);

            mvc.perform(post("/shop/api/v1/checkOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqs)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));

            then(productService).should().checkOrder(reqs);
        }

        @Test
        @DisplayName("проверка getSumTotalPriceOrder")
        void getSumTotalPriceOrder_sum() throws Exception {
            List<OrderRequest> reqs = List.of(new OrderRequest(UUID.randomUUID(), 5));
            given(productService.getSumTotalPriceOrder(reqs)).willReturn(123.45);

            mvc.perform(post("/shop/api/v1/getSumTotalPriceOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqs)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("123.45"));

            then(productService).should().getSumTotalPriceOrder(reqs);
        }
    }

    @Nested
    @DisplayName("Конечные точки изменения количества записей")
    @WithMockUser(roles = "ADMIN")
    class QuantityChanges {
        @Test
        @DisplayName("changeTotalQuantityProductsAfterCreateOrder → 200")
        void changeAfterCreateOrder() throws Exception {
            List<OrderRequest> reqs = List.of(new OrderRequest(UUID.randomUUID(), 1));

            mvc.perform(post("/shop/api/v1/changeTotalQuantityProductsAfterCreateOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqs)))
                    .andExpect(status().isOk());

            then(productService).should().changeTotalQuantityProductsAfterCreateOrder(reqs);
        }

        @Test
        @DisplayName("changeTotalQuantityProductsAfterRefundedOrder → 200")
        void changeAfterRefund() throws Exception {
            List<OrderRequest> reqs = List.of(new OrderRequest(UUID.randomUUID(), 1));

            mvc.perform(post("/shop/api/v1/changeTotalQuantityProductsAfterRefundedOrder")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqs)))
                    .andExpect(status().isOk());

            then(productService).should().changeTotalQuantityProductsAfterRefundedOrder(reqs);
        }
    }

    @Nested
    @DisplayName("CRUD для продуктов")
    @WithMockUser(roles = "ADMIN")
    class Crud {

        @Test
        @DisplayName("GET /getAllProducts → 200 + list")
        void getAllProducts() throws Exception {
            var dto1 = new ProductDto(UUID.randomUUID(), "A", 10, 100.0);
            var dto2 = new ProductDto(UUID.randomUUID(), "B", 5, 50.0);
            given(productService.getAllProducts(PageRequest.of(0, 2, Sort.by("name").ascending())))
                    .willReturn(List.of(dto1, dto2));

            mvc.perform(get("/shop/api/v1/getAllProducts")
                            .param("page", "0")
                            .param("size", "2")
                            .param("sort", "name,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("A"))
                    .andExpect(jsonPath("$[1].price").value(50.0));

            then(productService).should().getAllProducts(any(Pageable.class));
        }

        @Test
        @DisplayName("GET /getProducts/{id} → 200 + dto")
        void getProduct_ok() throws Exception {
            UUID id = UUID.randomUUID();
            var dto = new ProductDto(id, "X", 7, 77.0);
            given(productService.getProduct(id)).willReturn(dto);

            mvc.perform(get("/shop/api/v1/getProducts/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(7));

            then(productService).should().getProduct(id);
        }

        @Test
        @DisplayName("GET /getProducts/{id} → 404 если отсутствует")
        void getProduct_notFound() throws Exception {
            UUID id = UUID.randomUUID();
            given(productService.getProduct(id))
                    .willThrow(new ProductNotFoundException("Not found"));

            mvc.perform(get("/shop/api/v1/getProducts/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.messageError").value("Not found"));
        }

        @Test
        @DisplayName("POST /addProduct → 201 + новое dto")
        void addProduct() throws Exception {
            var req = new ProductDtoRequest("P", 3, 300.0);
            var dto = new ProductDto(UUID.randomUUID(), "P", 3, 300.0);
            given(productService.addProduct(req)).willReturn(dto);

            mvc.perform(post("/shop/api/v1/addProduct")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("P"));

            then(productService).should().addProduct(req);
        }

        @Test
        @DisplayName("DELETE /deleteProduct/{id} → 200")
        void delete_ok() throws Exception {
            UUID id = UUID.randomUUID();
            willDoNothing().given(productService).deleteProduct(id);

            mvc.perform(delete("/shop/api/v1/deleteProduct/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(content().string("Товар с articleId = " + id + " успешно удален"));

            then(productService).should().deleteProduct(id);
        }

        @Test
        @DisplayName("DELETE /deleteProduct/{id} → 404")
        void delete_notFound() throws Exception {
            UUID id = UUID.randomUUID();
            willThrow(new ProductNotFoundException("Nope"))
                    .given(productService).deleteProduct(id);

            mvc.perform(delete("/shop/api/v1/deleteProduct/{id}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.messageError").value("Nope"));
        }

        @Test
        @DisplayName("PUT /updateProduct/{id} → 200")
        void update_ok() throws Exception {
            UUID id = UUID.randomUUID();
            var req = new ProductDtoRequest("L", 2, 200.0);
            var out = new ProductDto(id, "L", 2, 200.0);
            given(productService.updateProduct(id, req)).willReturn(out);

            mvc.perform(put("/shop/api/v1/updateProduct/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.price").value(200.0));

            then(productService).should().updateProduct(id, req);
        }

        @Test
        @DisplayName("PUT /updateProduct/{id} → 404")
        void update_notFound() throws Exception {
            UUID id = UUID.randomUUID();
            var req = new ProductDtoRequest("T", 1, 10.0);
            given(productService.updateProduct(id, req))
                    .willThrow(new ProductNotFoundException("Missing"));

            mvc.perform(put("/shop/api/v1/updateProduct/{id}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.messageError").value("Missing"));
        }
    }
}