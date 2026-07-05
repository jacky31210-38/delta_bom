package com.delta.bom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.bom.dto.request.ScenarioItemRequest;
import com.delta.bom.dto.request.ScenarioRequest;
import com.delta.bom.dto.request.SubstitutionInput;
import com.delta.bom.dto.response.*;
import com.delta.bom.entity.BomComponent;
import com.delta.bom.entity.Material;
import com.delta.bom.entity.SubstituteScenario;
import com.delta.bom.entity.SubstituteScenarioItem;
import com.delta.bom.exception.BusinessException;
import com.delta.bom.exception.ScenarioNotFoundException;
import com.delta.bom.mapper.BomComponentMapper;
import com.delta.bom.mapper.MaterialMapper;
import com.delta.bom.mapper.SubstituteScenarioItemMapper;
import com.delta.bom.mapper.SubstituteScenarioMapper;
import com.delta.bom.service.BomService;
import com.delta.bom.service.MaterialFinder;
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

    private final BomComponentMapper bomComponentMapper;
    private final MaterialMapper materialMapper;
    private final SubstituteScenarioMapper scenarioMapper;
    private final SubstituteScenarioItemMapper scenarioItemMapper;
    private final MaterialFinder materialFinder;

    /**
     * 方案明細規則（比例/單價/替代料）與查詢當下輸入的數量，兩者結合後才是「這次要怎麼替換」的完整資訊。
     */
    private record AppliedSubstitution(SubstituteScenarioItem rule, BigDecimal qty) {
    }

    // ─────────────────────────────────────────────────────────────────
    // 查詢 BOM 完整結構
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "bomStructure", key = "#rootCode + '::' + (#substitutions != null ? #substitutions.toString() : 'NONE')")
    public BomNodeResponse getBomStructure(String rootCode, List<SubstitutionInput> substitutions) {
        log.debug("查詢 BOM 結構（cache miss）：{}，套用：{}", rootCode, substitutions);
        return buildBomTree(rootCode, substitutions);
    }

    /**
     * 實際建樹邏輯（無快取）。
     * getBomStructure 與 calculateCost 皆呼叫此方法，
     * 各自的 @Cacheable 獨立作用，避免 Spring AOP 自呼叫無法攔截的問題。
     */
    private BomNodeResponse buildBomTree(String rootCode, List<SubstitutionInput> substitutions) {
        materialFinder.getOrThrow(rootCode);

        // 用遞迴 CTE 一次展開整棵樹涉及的所有「父物料→子物料」邊，避免 N+1 查詢
        List<BomComponent> edges = bomComponentMapper.selectSubtreeEdges(rootCode);
        Map<String, List<AppliedSubstitution>> substituteMap = resolveSubstitutions(substitutions);

        // 替代料的名稱/單價要即時查 material，所以也要納入這次批次查詢的物料編碼範圍
        Set<String> materialCodes = new HashSet<>();
        materialCodes.add(rootCode);
        edges.forEach(e -> {
            materialCodes.add(e.getParentMaterialCode());
            materialCodes.add(e.getChildMaterialCode());
        });
        substituteMap.values().forEach(list ->
            list.forEach(a -> materialCodes.add(a.rule().getSubstituteCode())));

        Map<String, Material> materialMap = materialMapper.selectList(
                new LambdaQueryWrapper<Material>().in(Material::getMaterialCode, materialCodes)
            ).stream()
            .collect(Collectors.toMap(Material::getMaterialCode, m -> m));

        // 以 parentMaterialCode 分組建立父→子映射
        Map<String, List<BomComponent>> childrenMap = edges.stream()
            .collect(Collectors.groupingBy(BomComponent::getParentMaterialCode));

        // 根節點本身沒有「上層引用它的數量」，視為 1（要生產 1 個成品）
        return buildNode(rootCode, BigDecimal.ONE, childrenMap, materialMap, substituteMap, new HashSet<>(), 0, null);
    }

    private BomNodeResponse buildNode(String materialCode,
                                      BigDecimal quantity,
                                      Map<String, List<BomComponent>> childrenMap,
                                      Map<String, Material> materialMap,
                                      Map<String, List<AppliedSubstitution>> substituteMap,
                                      Set<String> visitedInPath,
                                      int depth,
                                      String parentMaterialCode) {
        if (depth > MAX_DEPTH) {
            throw new BusinessException("BOM 樹超過最大深度限制（" + MAX_DEPTH + " 層）：" + materialCode);
        }
        // 路徑上若已出現相同物料編碼，則為循環依賴
        if (!visitedInPath.add(materialCode)) {
            throw new BusinessException("偵測到 BOM 循環依賴：" + materialCode);
        }

        Material material = materialMap.get(materialCode);
        List<AppliedSubstitution> matches = substituteMap.getOrDefault(materialCode, Collections.emptyList());

        // 允許同一顆主料被多個方案的規則「共同覆蓋」，但這次輸入的數量合計不可超過主料 BOM 數量。
        // 查詢結構與計算成本都會走到這裡，確保兩個入口的驗證規則一致。
        BigDecimal totalCovered = matches.stream()
            .map(AppliedSubstitution::qty)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCovered.compareTo(quantity) > 0) {
            throw new BusinessException(String.format(
                "套用的替代數量合計(%s)超過主料 BOM 數量(%s)，物料編碼：%s",
                totalCovered, quantity, materialCode));
        }

        boolean hasSubstitute = !matches.isEmpty();
        List<SubstituteInfoResponse> substituteInfos = matches.stream()
            .map(a -> {
                // 替代料的名稱/單價一律即時查 material，避免物料主檔改價後這裡沒同步更新
                Material substituteMaterial = materialMap.get(a.rule().getSubstituteCode());
                return SubstituteInfoResponse.builder()
                    .scenarioKey(a.rule().getScenarioKey())
                    .substituteCode(a.rule().getSubstituteCode())
                    .substituteName(substituteMaterial != null ? substituteMaterial.getMaterialName() : null)
                    .substituteQty(a.qty())
                    .substituteRatio(a.rule().getSubstituteRatio())
                    .unitPrice(substituteMaterial != null ? substituteMaterial.getUnitPrice() : null)
                    .reason(a.rule().getReason())
                    .build();
            })
            .collect(Collectors.toList());

        // 一顆子物料的組成（例如 POWER-MODULE 底下有哪些子件）只定義一次，
        // 這裡沿 childrenMap（依父物料編碼分組的邊）往下展開，天然支援同一物料被多處共用。
        List<BomNodeResponse> children = childrenMap
            .getOrDefault(materialCode, Collections.emptyList())
            .stream()
            .map(edge -> buildNode(edge.getChildMaterialCode(), edge.getQuantity(), childrenMap, materialMap, substituteMap,
                                   new HashSet<>(visitedInPath), depth + 1, materialCode))
            .collect(Collectors.toList());

        return BomNodeResponse.builder()
            .itemCode(materialCode)
            .itemName(material != null ? material.getMaterialName() : null)
            .unit(material != null ? material.getUnit() : null)
            .quantity(quantity)
            .unitPrice(material != null ? material.getUnitPrice() : null)
            .level(depth)
            .parentCode(parentMaterialCode)
            .hasSubstitute(hasSubstitute)
            .substituteInfos(substituteInfos)
            .children(children)
            .build();
    }

    // ─────────────────────────────────────────────────────────────────
    // BOM 成本計算
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "bomCost", key = "#rootCode + '::' + (#substitutions != null ? #substitutions.toString() : 'NONE')")
    public BomCostResponse calculateCost(String rootCode, List<SubstitutionInput> substitutions) {
        log.debug("計算 BOM 成本（cache miss）：{}，套用：{}", rootCode, substitutions);
        BomNodeResponse root = buildBomTree(rootCode, substitutions);

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
     * DFS 遞迴計算成本。一顆葉料可能同時被多個方案的規則「部分覆蓋」，
     * 因此每個葉節點可能產出多筆明細：每個替代配對各一筆，加上（若有剩餘未覆蓋數量）一筆主料原價。
     *
     * @param node             當前節點
     * @param parentMultiplier 所有祖先節點數量的累積乘積
     * @param details          明細收集器
     * @return 此節點（含所有後代）的總成本
     */
    private BigDecimal calcCostDfs(BomNodeResponse node,
                                   BigDecimal parentMultiplier,
                                   List<BomCostResponse.CostDetailItem> details) {
        // 非葉節點：向下遞迴，自身無單價
        if (!node.getChildren().isEmpty()) {
            BigDecimal effectiveQty = node.getQuantity().multiply(parentMultiplier);
            BigDecimal total = BigDecimal.ZERO;
            for (BomNodeResponse child : node.getChildren()) {
                total = total.add(calcCostDfs(child, effectiveQty, details));
            }
            return total;
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal coveredQty = BigDecimal.ZERO;

        for (SubstituteInfoResponse info : node.getSubstituteInfos()) {
            coveredQty = coveredQty.add(info.getSubstituteQty());
            BigDecimal ratio = info.getSubstituteRatio() != null ? info.getSubstituteRatio() : BigDecimal.ONE;
            // 替代料單價來自物料主檔，理論上寫入時已檢查過必須有單價，這裡仍防禦性地擋 null，避免物料事後被改成沒有單價
            BigDecimal substitutePrice = info.getUnitPrice() != null ? info.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal subEffectiveQty = info.getSubstituteQty().multiply(parentMultiplier).multiply(ratio);
            BigDecimal subCost = subEffectiveQty.multiply(substitutePrice);
            total = total.add(subCost);

            details.add(BomCostResponse.CostDetailItem.builder()
                .itemCode(node.getItemCode())
                .itemName(node.getItemName())
                .effectiveItemCode(info.getSubstituteCode())
                .effectiveItemName(info.getSubstituteName())
                .scenarioKey(info.getScenarioKey())
                .bomQuantity(info.getSubstituteQty())
                .effectiveQty(subEffectiveQty)
                .unitPrice(substitutePrice)
                .subtotal(subCost.setScale(4, RoundingMode.HALF_UP))
                .substituted(true)
                .build());
        }

        // 剩餘未被任何方案覆蓋的數量，使用主料原價（buildNode 已保證 coveredQty 不超過 node.quantity）
        BigDecimal remainQty = node.getQuantity().subtract(coveredQty);
        if (remainQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainEffectiveQty = remainQty.multiply(parentMultiplier);
            BigDecimal price = node.getUnitPrice() != null ? node.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal remainCost = remainEffectiveQty.multiply(price);
            total = total.add(remainCost);

            details.add(BomCostResponse.CostDetailItem.builder()
                .itemCode(node.getItemCode())
                .itemName(node.getItemName())
                .effectiveItemCode(node.getItemCode())
                .effectiveItemName(node.getItemName())
                .scenarioKey(null)
                .bomQuantity(remainQty)
                .effectiveQty(remainEffectiveQty)
                .unitPrice(price)
                .subtotal(remainCost.setScale(4, RoundingMode.HALF_UP))
                .substituted(false)
                .build());
        }

        return total;
    }

    // ─────────────────────────────────────────────────────────────────
    // 替代方案管理
    // ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public ScenarioResponse createScenario(ScenarioRequest request) {
        SubstituteScenario existing = scenarioMapper.selectOne(
            new LambdaQueryWrapper<SubstituteScenario>()
                .eq(SubstituteScenario::getScenarioKey, request.getScenarioKey())
        );
        if (existing != null) {
            throw new BusinessException("方案 key 已存在：" + request.getScenarioKey());
        }

        SubstituteScenario scenario = SubstituteScenario.builder()
            .scenarioKey(request.getScenarioKey())
            .scenarioName(request.getScenarioName())
            .description(request.getDescription())
            .build();
        scenarioMapper.insert(scenario);
        log.info("新增替代方案：{}", request.getScenarioKey());

        return toScenarioResponse(scenario, 0);
    }

    @Override
    public List<ScenarioResponse> listScenarios() {
        List<SubstituteScenario> scenarios = scenarioMapper.selectList(null);
        return scenarios.stream()
            .map(s -> toScenarioResponse(s, scenarioItemMapper.selectCount(
                new LambdaQueryWrapper<SubstituteScenarioItem>()
                    .eq(SubstituteScenarioItem::getScenarioKey, s.getScenarioKey())
            )))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public void deleteScenario(String scenarioKey) {
        SubstituteScenario scenario = getScenarioOrThrow(scenarioKey);

        scenarioItemMapper.delete(
            new LambdaQueryWrapper<SubstituteScenarioItem>()
                .eq(SubstituteScenarioItem::getScenarioKey, scenarioKey)
        );
        scenarioMapper.deleteById(scenario.getId());
        log.info("刪除替代方案：{}", scenarioKey);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public ScenarioItemResponse upsertScenarioItem(ScenarioItemRequest request) {
        getScenarioOrThrow(request.getScenarioKey());

        // 確認主料存在（規則本身不記錄數量，數量是查詢當下才輸入，故不在此驗證 BOM 數量）
        materialFinder.getOrThrow(request.getPrimaryMaterialCode());

        // 替代料的名稱/單價一律即時查 material，這裡順便確認替代料存在、且必須已設定單價，
        // 否則之後算成本時無從得知該用多少單價
        Material substituteMaterial = materialFinder.getOrThrow(request.getSubstituteMaterialCode());
        if (substituteMaterial.getUnitPrice() == null) {
            throw new BusinessException("替代料 " + request.getSubstituteMaterialCode() + " 尚未在物料主檔設定單價，無法作為替代選項");
        }

        BigDecimal ratio = request.getSubstituteRatio() != null ? request.getSubstituteRatio() : BigDecimal.ONE;

        // UPSERT：同一方案內，有則更新、無則新增
        SubstituteScenarioItem existing = scenarioItemMapper.selectOne(
            new LambdaQueryWrapper<SubstituteScenarioItem>()
                .eq(SubstituteScenarioItem::getScenarioKey, request.getScenarioKey())
                .eq(SubstituteScenarioItem::getPrimaryCode, request.getPrimaryMaterialCode())
        );

        if (existing == null) {
            SubstituteScenarioItem item = SubstituteScenarioItem.builder()
                .scenarioKey(request.getScenarioKey())
                .primaryCode(request.getPrimaryMaterialCode())
                .substituteCode(request.getSubstituteMaterialCode())
                .reason(request.getReason())
                .substituteRatio(ratio)
                .build();
            scenarioItemMapper.insert(item);
            log.info("方案 {} 新增替代規則：{} → {}", request.getScenarioKey(),
                request.getPrimaryMaterialCode(), request.getSubstituteMaterialCode());
            return toScenarioItemResponse(item, substituteMaterial);
        } else {
            existing.setSubstituteCode(request.getSubstituteMaterialCode());
            existing.setReason(request.getReason());
            existing.setSubstituteRatio(ratio);
            scenarioItemMapper.updateById(existing);
            log.info("方案 {} 更新替代規則：{} → {}", request.getScenarioKey(),
                request.getPrimaryMaterialCode(), request.getSubstituteMaterialCode());
            return toScenarioItemResponse(existing, substituteMaterial);
        }
    }

    @Override
    public List<ScenarioItemResponse> listScenarioItems(String scenarioKey) {
        getScenarioOrThrow(scenarioKey);
        return toScenarioItemResponses(scenarioItemMapper.selectList(
            new LambdaQueryWrapper<SubstituteScenarioItem>()
                .eq(SubstituteScenarioItem::getScenarioKey, scenarioKey)
        ));
    }

    @Override
    public List<ScenarioItemResponse> listAllScenarioItems() {
        return toScenarioItemResponses(scenarioItemMapper.selectList(null));
    }

    /**
     * 批次解析一批方案明細規則各自的替代料名稱/單價，避免逐筆查詢造成 N+1。
     */
    private List<ScenarioItemResponse> toScenarioItemResponses(List<SubstituteScenarioItem> items) {
        Set<String> substituteCodes = items.stream()
            .map(SubstituteScenarioItem::getSubstituteCode)
            .collect(Collectors.toSet());
        Map<String, Material> materialMap = substituteCodes.isEmpty()
            ? Collections.emptyMap()
            : materialMapper.selectList(
                    new LambdaQueryWrapper<Material>().in(Material::getMaterialCode, substituteCodes)
                ).stream()
                .collect(Collectors.toMap(Material::getMaterialCode, m -> m));

        return items.stream()
            .map(item -> toScenarioItemResponse(item, materialMap.get(item.getSubstituteCode())))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public void deleteScenarioItem(String scenarioKey, String primaryCode) {
        getScenarioOrThrow(scenarioKey);
        scenarioItemMapper.delete(
            new LambdaQueryWrapper<SubstituteScenarioItem>()
                .eq(SubstituteScenarioItem::getScenarioKey, scenarioKey)
                .eq(SubstituteScenarioItem::getPrimaryCode, primaryCode)
        );
        log.info("方案 {} 刪除替代規則：{}", scenarioKey, primaryCode);
    }

    // ─────────────────────────────────────────────────────────────────
    // 工具方法
    // ─────────────────────────────────────────────────────────────────

    /**
     * 將使用者這次查詢輸入的 (scenarioKey, primaryCode, qty) 清單，
     * 對照到方案明細規則（比例/單價/替代料），組成 primaryCode -> 已套用替代 的對照表。
     * 傳入的每個 scenarioKey、每個 (scenarioKey, primaryCode) 規則都必須存在，找不到就直接失敗，
     * 不會靜默忽略打錯字或不存在的輸入。
     */
    private Map<String, List<AppliedSubstitution>> resolveSubstitutions(List<SubstitutionInput> substitutions) {
        if (substitutions == null || substitutions.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<AppliedSubstitution>> result = new HashMap<>();
        for (SubstitutionInput input : substitutions) {
            getScenarioOrThrow(input.getScenarioKey());

            SubstituteScenarioItem rule = scenarioItemMapper.selectOne(
                new LambdaQueryWrapper<SubstituteScenarioItem>()
                    .eq(SubstituteScenarioItem::getScenarioKey, input.getScenarioKey())
                    .eq(SubstituteScenarioItem::getPrimaryCode, input.getPrimaryCode())
            );
            if (rule == null) {
                throw new BusinessException(String.format(
                    "方案 %s 沒有針對主料 %s 定義替代規則", input.getScenarioKey(), input.getPrimaryCode()));
            }

            result.computeIfAbsent(input.getPrimaryCode(), k -> new ArrayList<>())
                .add(new AppliedSubstitution(rule, input.getQty()));
        }
        return result;
    }

    private SubstituteScenario getScenarioOrThrow(String scenarioKey) {
        SubstituteScenario scenario = scenarioMapper.selectOne(
            new LambdaQueryWrapper<SubstituteScenario>()
                .eq(SubstituteScenario::getScenarioKey, scenarioKey)
        );
        if (scenario == null) {
            throw new ScenarioNotFoundException(scenarioKey);
        }
        return scenario;
    }

    private ScenarioResponse toScenarioResponse(SubstituteScenario scenario, long itemCount) {
        return ScenarioResponse.builder()
            .id(scenario.getId())
            .scenarioKey(scenario.getScenarioKey())
            .scenarioName(scenario.getScenarioName())
            .description(scenario.getDescription())
            .itemCount(itemCount)
            .createdAt(scenario.getCreatedAt())
            .updatedAt(scenario.getUpdatedAt())
            .build();
    }

    private ScenarioItemResponse toScenarioItemResponse(SubstituteScenarioItem item, Material substituteMaterial) {
        return ScenarioItemResponse.builder()
            .id(item.getId())
            .scenarioKey(item.getScenarioKey())
            .primaryCode(item.getPrimaryCode())
            .substituteCode(item.getSubstituteCode())
            .substituteName(substituteMaterial != null ? substituteMaterial.getMaterialName() : null)
            .reason(item.getReason())
            .substituteRatio(item.getSubstituteRatio())
            .unitPrice(substituteMaterial != null ? substituteMaterial.getUnitPrice() : null)
            .version(item.getVersion())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .build();
    }
}
