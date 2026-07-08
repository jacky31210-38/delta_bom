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
import com.delta.bom.exception.OptimisticLockConflictException;
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

    /**
     * {@inheritDoc}
     */
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
     *
     * @param rootCode      根節點物料編碼
     * @param substitutions 這次要套用的替代規則清單，null 或空清單代表不套用任何替代
     * @return 展開後的樹狀結構
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

    /**
     * 遞迴建出一個節點及其所有子孫節點。
     *
     * @param materialCode       目前節點的物料編碼
     * @param quantity           目前節點在其直接父節點 BOM 裡的數量（邊本身定義的數量，不含祖先層的累乘）
     * @param childrenMap        依父物料編碼分組的「父→子」組成關係，用來往下展開子節點
     * @param materialMap        物料編碼對應到物料實體的批次查詢結果，用來取得名稱/單價
     * @param substituteMap      物料編碼對應到已套用替代規則清單的對照表
     * @param visitedInPath      目前路徑上已經走過的物料編碼，用來偵測循環依賴
     * @param depth              目前節點的深度（根節點為 0）
     * @param parentMaterialCode 直接父節點的物料編碼，根節點為 null
     * @return 這個節點（含所有子孫節點）組成的樹狀結構
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public ScenarioItemResponse createScenarioItem(ScenarioItemRequest request) {
        getScenarioOrThrow(request.getScenarioKey());

        // 確認主料存在（規則本身不記錄數量，數量是查詢當下才輸入，故不在此驗證 BOM 數量）
        materialFinder.getOrThrow(request.getPrimaryMaterialCode());
        Material substituteMaterial = getPricedSubstituteOrThrow(request.getSubstituteMaterialCode());

        // 新增只能建立全新規則，同一方案內同一顆主料若已有規則，一律擋下、不覆蓋，
        // 避免「新增」被誤用成靜默覆寫既有規則（見同方案同主料只能有一條規則的唯一鍵限制）
        SubstituteScenarioItem existing = findScenarioItem(request.getScenarioKey(), request.getPrimaryMaterialCode());
        if (existing != null) {
            throw new BusinessException(String.format(
                "方案 %s 對主料 %s 已有替代規則（替代料 %s），請改用「編輯」修改既有規則",
                request.getScenarioKey(), request.getPrimaryMaterialCode(), existing.getSubstituteCode()));
        }

        SubstituteScenarioItem item = SubstituteScenarioItem.builder()
            .scenarioKey(request.getScenarioKey())
            .primaryCode(request.getPrimaryMaterialCode())
            .substituteCode(request.getSubstituteMaterialCode())
            .reason(request.getReason())
            .substituteRatio(request.getSubstituteRatio() != null ? request.getSubstituteRatio() : BigDecimal.ONE)
            .build();
        scenarioItemMapper.insert(item);
        log.info("方案 {} 新增替代規則：{} → {}", request.getScenarioKey(),
            request.getPrimaryMaterialCode(), request.getSubstituteMaterialCode());
        return toScenarioItemResponse(item, substituteMaterial);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "bomStructure", allEntries = true),
        @CacheEvict(value = "bomCost", allEntries = true)
    })
    public ScenarioItemResponse updateScenarioItem(ScenarioItemRequest request) {
        getScenarioOrThrow(request.getScenarioKey());

        materialFinder.getOrThrow(request.getPrimaryMaterialCode());
        Material substituteMaterial = getPricedSubstituteOrThrow(request.getSubstituteMaterialCode());

        if (request.getVersion() == null) {
            throw new BusinessException("更新既有替代規則時必須提供 version，避免覆蓋他人異動");
        }

        // 更新只能修改既有規則，找不到就直接失敗，不會靜默改成新增（避免跟「新增」的職責混在一起）
        SubstituteScenarioItem existing = findScenarioItem(request.getScenarioKey(), request.getPrimaryMaterialCode());
        if (existing == null) {
            throw new BusinessException(String.format(
                "方案 %s 尚無主料 %s 的替代規則，請改用「新增」建立", request.getScenarioKey(), request.getPrimaryMaterialCode()));
        }

        existing.setSubstituteCode(request.getSubstituteMaterialCode());
        existing.setReason(request.getReason());
        existing.setSubstituteRatio(request.getSubstituteRatio() != null ? request.getSubstituteRatio() : BigDecimal.ONE);
        // 用前端傳回來的版本（而非剛剛查到的最新版本）去比對，樂觀鎖才會在版本不符時真的擋下來
        existing.setVersion(request.getVersion());
        if (scenarioItemMapper.updateById(existing) == 0) {
            throw new OptimisticLockConflictException(String.format(
                "方案 %s 對主料 %s 的替代規則已被其他人修改，請重新整理後再試",
                request.getScenarioKey(), request.getPrimaryMaterialCode()));
        }
        log.info("方案 {} 更新替代規則：{} → {}", request.getScenarioKey(),
            request.getPrimaryMaterialCode(), request.getSubstituteMaterialCode());
        return toScenarioItemResponse(existing, substituteMaterial);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ScenarioItemResponse> listScenarioItems(String scenarioKey) {
        getScenarioOrThrow(scenarioKey);
        return toScenarioItemResponses(scenarioItemMapper.selectList(
            new LambdaQueryWrapper<SubstituteScenarioItem>()
                .eq(SubstituteScenarioItem::getScenarioKey, scenarioKey)
        ));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ScenarioItemResponse> listAllScenarioItems() {
        return toScenarioItemResponses(scenarioItemMapper.selectList(null));
    }

    /**
     * 批次解析一批方案明細規則各自的替代料名稱/單價，避免逐筆查詢造成 N+1。
     *
     * @param items 要轉換的方案明細規則清單
     * @return 轉換後的回應內容清單
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

    /**
     * {@inheritDoc}
     */
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
     *
     * @param substitutions 使用者這次查詢輸入的替代規則清單，null 或空清單代表沒有套用任何替代
     * @return 主料編碼對應到「已套用替代規則＋數量」清單的對照表
     */
    private Map<String, List<AppliedSubstitution>> resolveSubstitutions(List<SubstitutionInput> substitutions) {
        if (substitutions == null || substitutions.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<AppliedSubstitution>> result = new HashMap<>();
        for (SubstitutionInput input : substitutions) {
            getScenarioOrThrow(input.getScenarioKey());

            SubstituteScenarioItem rule = findScenarioItem(input.getScenarioKey(), input.getPrimaryCode());
            if (rule == null) {
                throw new BusinessException(String.format(
                    "方案 %s 沒有針對主料 %s 定義替代規則", input.getScenarioKey(), input.getPrimaryCode()));
            }

            result.computeIfAbsent(input.getPrimaryCode(), k -> new ArrayList<>())
                .add(new AppliedSubstitution(rule, input.getQty()));
        }
        return result;
    }

    /**
     * 依方案 key 查詢方案，查無資料時直接丟例外。
     *
     * @param scenarioKey 方案 key
     * @return 查到的方案
     */
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

    /**
     * 查詢指定方案內、指定主料目前的替代規則，查無資料回傳 null（是否視為錯誤由呼叫端決定）。
     *
     * @param scenarioKey 方案 key
     * @param primaryCode 主料編碼
     * @return 查到的規則，查無資料則為 null
     */
    private SubstituteScenarioItem findScenarioItem(String scenarioKey, String primaryCode) {
        return scenarioItemMapper.selectOne(
            new LambdaQueryWrapper<SubstituteScenarioItem>()
                .eq(SubstituteScenarioItem::getScenarioKey, scenarioKey)
                .eq(SubstituteScenarioItem::getPrimaryCode, primaryCode)
        );
    }

    /**
     * 確認替代料存在、且已在物料主檔設定單價，否則之後算成本時無從得知該用多少單價。
     *
     * @param substituteMaterialCode 替代料編碼
     * @return 已確認有單價的替代料
     */
    private Material getPricedSubstituteOrThrow(String substituteMaterialCode) {
        Material substituteMaterial = materialFinder.getOrThrow(substituteMaterialCode);
        if (substituteMaterial.getUnitPrice() == null) {
            throw new BusinessException("替代料 " + substituteMaterialCode + " 尚未在物料主檔設定單價，無法作為替代選項");
        }
        return substituteMaterial;
    }

    /**
     * 把 SubstituteScenario 實體轉成對外的回應格式。
     *
     * @param scenario  方案實體
     * @param itemCount 這個方案底下目前有幾筆替代規則
     * @return 方案回應內容
     */
    private ScenarioResponse toScenarioResponse(SubstituteScenario scenario, long itemCount) {
        return ScenarioResponse.builder()
            .id(scenario.getId())
            .scenarioKey(scenario.getScenarioKey())
            .scenarioName(scenario.getScenarioName())
            .description(scenario.getDescription())
            .itemCount(itemCount)
            .version(scenario.getVersion())
            .createdAt(scenario.getCreatedAt())
            .updatedAt(scenario.getUpdatedAt())
            .build();
    }

    /**
     * 把 SubstituteScenarioItem 實體（加上即時查到的替代料資訊）轉成對外的回應格式。
     *
     * @param item               方案明細規則實體
     * @param substituteMaterial 這筆規則對應的替代料，可能為 null（物料主檔資料異常時的防禦）
     * @return 方案明細回應內容
     */
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
