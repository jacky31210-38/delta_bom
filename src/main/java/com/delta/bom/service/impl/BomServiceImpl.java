package com.delta.bom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.bom.dto.request.SubstituteRequest;
import com.delta.bom.dto.response.*;
import com.delta.bom.entity.BomItem;
import com.delta.bom.entity.SubstituteMaterial;
import com.delta.bom.exception.BomNotFoundException;
import com.delta.bom.exception.BusinessException;
import com.delta.bom.mapper.BomItemMapper;
import com.delta.bom.mapper.SubstituteMaterialMapper;
import com.delta.bom.service.BomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BomServiceImpl implements BomService {

    private static final int MAX_DEPTH = 50;

    private final BomItemMapper bomItemMapper;
    private final SubstituteMaterialMapper substituteMaterialMapper;

    // ─────────────────────────────────────────────────────────────────
    // 查詢 BOM 完整結構
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "bomStructure", key = "#rootCode")
    public BomNodeResponse getBomStructure(String rootCode) {
        log.debug("查詢 BOM 結構（cache miss）：{}", rootCode);
        return buildBomTree(rootCode);
    }

    /**
     * 實際建樹邏輯（無快取）。
     * getBomStructure 與 calculateCost 皆呼叫此方法，
     * 各自的 @Cacheable 獨立作用，避免 Spring AOP 自呼叫無法攔截的問題。
     */
    private BomNodeResponse buildBomTree(String rootCode) {
        // 用遞迴 CTE 一次拉取整棵子樹，避免 N+1 查詢
        List<BomItem> nodes = bomItemMapper.selectDescendantsWithSelf(rootCode);
        if (nodes.isEmpty()) {
            throw new BomNotFoundException(rootCode);
        }

        Map<String, SubstituteMaterial> substituteMap = loadSubstituteMap();

        BomItem root = nodes.stream()
            .filter(n -> n.getItemCode().equals(rootCode))
            .findFirst()
            .orElseThrow(() -> new BomNotFoundException(rootCode));

        // 以 parentCode 分組建立父→子映射
        Map<String, List<BomItem>> childrenMap = nodes.stream()
            .filter(n -> n.getParentCode() != null)
            .collect(Collectors.groupingBy(BomItem::getParentCode));

        return buildNode(root, childrenMap, substituteMap, new HashSet<>(), 0);
    }

    private BomNodeResponse buildNode(BomItem item,
                                      Map<String, List<BomItem>> childrenMap,
                                      Map<String, SubstituteMaterial> substituteMap,
                                      Set<String> visitedInPath,
                                      int depth) {
        if (depth > MAX_DEPTH) {
            throw new BusinessException("BOM 樹超過最大深度限制（" + MAX_DEPTH + " 層）：" + item.getItemCode());
        }
        // 路徑上若已出現相同編碼，則為循環依賴
        if (!visitedInPath.add(item.getItemCode())) {
            throw new BusinessException("偵測到 BOM 循環依賴：" + item.getItemCode());
        }

        SubstituteMaterial sub = substituteMap.get(item.getItemCode());
        boolean hasSubstitute = (sub != null);

        List<BomNodeResponse> children = childrenMap
            .getOrDefault(item.getItemCode(), Collections.emptyList())
            .stream()
            .map(child -> buildNode(child, childrenMap, substituteMap,
                                    new HashSet<>(visitedInPath), depth + 1))
            .collect(Collectors.toList());

        BomNodeResponse.BomNodeResponseBuilder builder = BomNodeResponse.builder()
            .itemCode(item.getItemCode())
            .itemName(item.getItemName())
            .unit(item.getUnit())
            .quantity(item.getQuantity())
            .unitPrice(item.getUnitPrice())
            .level(item.getLevel())
            .parentCode(item.getParentCode())
            .hasSubstitute(hasSubstitute)
            .effectiveItemCode(hasSubstitute ? sub.getSubstituteCode() : item.getItemCode())
            .effectiveItemName(hasSubstitute ? sub.getSubstituteName() : item.getItemName())
            .effectiveUnitPrice(hasSubstitute ? sub.getUnitPrice() : item.getUnitPrice())
            .children(children);

        if (hasSubstitute) {
            builder.substituteInfo(SubstituteInfoResponse.builder()
                .substituteCode(sub.getSubstituteCode())
                .substituteName(sub.getSubstituteName())
                .substituteQty(sub.getSubstituteQty())
                .unitPrice(sub.getUnitPrice())
                .reason(sub.getReason())
                .build());
        }

        return builder.build();
    }

    // ─────────────────────────────────────────────────────────────────
    // BOM 成本計算
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "bomCost", key = "#rootCode")
    public BomCostResponse calculateCost(String rootCode) {
        log.debug("計算 BOM 成本（cache miss）：{}", rootCode);
        BomNodeResponse root = buildBomTree(rootCode);

        List<BomCostResponse.CostDetailItem> details = new ArrayList<>();
        BigDecimal total = calcCostDfs(root, BigDecimal.ONE, details);

        return BomCostResponse.builder()
            .rootCode(rootCode)
            .rootName(root.getItemName())
            .totalCost(total.setScale(4, RoundingMode.HALF_UP))
            .details(details)
            .build();
    }

    /**
     * DFS 遞迴計算成本。
     *
     * @param node             當前節點
     * @param parentMultiplier 所有祖先節點數量的累積乘積
     * @param details          葉節點成本明細收集器
     * @return 此節點（含所有後代）的總成本
     */
    private BigDecimal calcCostDfs(BomNodeResponse node,
                                   BigDecimal parentMultiplier,
                                   List<BomCostResponse.CostDetailItem> details) {
        BigDecimal effectiveQty = node.getQuantity().multiply(parentMultiplier);

        // 非葉節點：向下遞迴，自身無單價
        if (!node.getChildren().isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (BomNodeResponse child : node.getChildren()) {
                total = total.add(calcCostDfs(child, effectiveQty, details));
            }
            return total;
        }

        // 葉節點：計算成本（考慮部分替換）
        BigDecimal cost;
        boolean substituted = node.isHasSubstitute();

        if (substituted && node.getSubstituteInfo() != null) {
            SubstituteInfoResponse subInfo = node.getSubstituteInfo();
            // 替代料有效數量 = substituteQty × 父層倍乘
            BigDecimal subEffectiveQty = subInfo.getSubstituteQty().multiply(parentMultiplier);
            BigDecimal remainQty = effectiveQty.subtract(subEffectiveQty);

            BigDecimal subCost = subEffectiveQty.multiply(subInfo.getUnitPrice());
            // 剩餘數量使用主料原價（部分替換情境）
            BigDecimal mainCost = remainQty.compareTo(BigDecimal.ZERO) > 0
                ? remainQty.multiply(node.getUnitPrice() != null ? node.getUnitPrice() : BigDecimal.ZERO)
                : BigDecimal.ZERO;
            cost = subCost.add(mainCost);
        } else {
            BigDecimal price = node.getEffectiveUnitPrice() != null
                ? node.getEffectiveUnitPrice() : BigDecimal.ZERO;
            cost = effectiveQty.multiply(price);
        }

        details.add(BomCostResponse.CostDetailItem.builder()
            .itemCode(node.getItemCode())
            .itemName(node.getItemName())
            .effectiveItemCode(node.getEffectiveItemCode())
            .effectiveItemName(node.getEffectiveItemName())
            .bomQuantity(node.getQuantity())
            .effectiveQty(effectiveQty)
            .unitPrice(node.getEffectiveUnitPrice())
            .subtotal(cost.setScale(4, RoundingMode.HALF_UP))
            .substituted(substituted)
            .build());

        return cost;
    }

    // ─────────────────────────────────────────────────────────────────
    // 替代料管理
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public void applySubstitute(SubstituteRequest request) {
        // 確認主料存在
        BomItem primary = bomItemMapper.selectOne(
            new LambdaQueryWrapper<BomItem>()
                .eq(BomItem::getItemCode, request.getPrimaryMaterialCode())
        );
        if (primary == null) {
            throw new BomNotFoundException(request.getPrimaryMaterialCode());
        }

        // 替代料數量不可超過主料 BOM 數量
        if (request.getSubstituteQty().compareTo(primary.getQuantity()) > 0) {
            throw new BusinessException(String.format(
                "替代料數量(%s)不可超過主料 BOM 數量(%s)",
                request.getSubstituteQty(), primary.getQuantity()
            ));
        }

        // UPSERT：有則更新、無則新增
        SubstituteMaterial existing = substituteMaterialMapper.selectOne(
            new LambdaQueryWrapper<SubstituteMaterial>()
                .eq(SubstituteMaterial::getPrimaryCode, request.getPrimaryMaterialCode())
        );

        if (existing == null) {
            SubstituteMaterial sub = SubstituteMaterial.builder()
                .primaryCode(request.getPrimaryMaterialCode())
                .substituteCode(request.getSubstituteMaterialCode())
                .substituteName(request.getSubstituteMaterialName())
                .reason(request.getReason())
                .substituteQty(request.getSubstituteQty())
                .unitPrice(request.getUnitPrice())
                .build();
            substituteMaterialMapper.insert(sub);
            log.info("新增替代料：{} → {}", request.getPrimaryMaterialCode(), request.getSubstituteMaterialCode());
        } else {
            existing.setSubstituteCode(request.getSubstituteMaterialCode());
            existing.setSubstituteName(request.getSubstituteMaterialName());
            existing.setReason(request.getReason());
            existing.setSubstituteQty(request.getSubstituteQty());
            existing.setUnitPrice(request.getUnitPrice());
            substituteMaterialMapper.updateById(existing);
            log.info("更新替代料：{} → {}", request.getPrimaryMaterialCode(), request.getSubstituteMaterialCode());
        }
    }

    @Override
    public SubstituteResponse getSubstitute(String primaryCode) {
        SubstituteMaterial sub = substituteMaterialMapper.selectOne(
            new LambdaQueryWrapper<SubstituteMaterial>()
                .eq(SubstituteMaterial::getPrimaryCode, primaryCode)
        );
        if (sub == null) {
            throw new BomNotFoundException("查無替代料，主料編碼：" + primaryCode);
        }
        return toSubstituteResponse(sub);
    }

    // ─────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────

    private Map<String, SubstituteMaterial> loadSubstituteMap() {
        return substituteMaterialMapper.selectList(null)
            .stream()
            .collect(Collectors.toMap(SubstituteMaterial::getPrimaryCode, s -> s));
    }

    private SubstituteResponse toSubstituteResponse(SubstituteMaterial sub) {
        return SubstituteResponse.builder()
            .id(sub.getId())
            .primaryCode(sub.getPrimaryCode())
            .substituteCode(sub.getSubstituteCode())
            .substituteName(sub.getSubstituteName())
            .reason(sub.getReason())
            .substituteQty(sub.getSubstituteQty())
            .unitPrice(sub.getUnitPrice())
            .version(sub.getVersion())
            .createdAt(sub.getCreatedAt())
            .updatedAt(sub.getUpdatedAt())
            .build();
    }
}
