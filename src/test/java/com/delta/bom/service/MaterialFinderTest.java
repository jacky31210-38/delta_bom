package com.delta.bom.service;

import com.delta.bom.entity.Material;
import com.delta.bom.exception.BomNotFoundException;
import com.delta.bom.mapper.MaterialMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialFinderTest {

    @Mock
    private MaterialMapper materialMapper;

    @InjectMocks
    private MaterialFinder materialFinder;

    @Test
    void getOrThrow_returnsMaterial_whenFound() {
        Material material = Material.builder().materialCode("IC-MCU").materialName("微控制器").build();
        when(materialMapper.selectOne(any())).thenReturn(material);

        Material result = materialFinder.getOrThrow("IC-MCU");

        assertThat(result).isSameAs(material);
    }

    @Test
    void getOrThrow_throwsBomNotFoundException_whenMissing() {
        when(materialMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> materialFinder.getOrThrow("NOT-EXIST"))
            .isInstanceOf(BomNotFoundException.class)
            .hasMessageContaining("NOT-EXIST");
    }
}
