package dev.freelocs.aetherion.common.features

/**
 * Single source of truth for categories and features. The GUI never hardcodes either; it renders
 * whatever is registered here. [validate] enforces the schema invariants (unique ids, known
 * categories, existing and acyclic dependencies, unique setting ids).
 */
object FeatureRegistry {
    private val groupMap = LinkedHashMap<String, GroupDefinition>()
    private val categoryMap = LinkedHashMap<String, CategoryDefinition>()
    private val featureMap = LinkedHashMap<String, FeatureDefinition>()

    // Read-only snapshots are cached because views query them every frame; any registration invalidates them.
    private var groupCache: List<GroupDefinition>? = null
    private var categoryCache: List<CategoryDefinition>? = null
    private var featureCache: List<FeatureDefinition>? = null
    private val byCategoryCache = HashMap<String, List<FeatureDefinition>>()
    private val categoriesInCache = HashMap<String, List<CategoryDefinition>>()

    private fun invalidate() {
        groupCache = null; categoryCache = null; featureCache = null; byCategoryCache.clear(); categoriesInCache.clear()
    }

    val groups: List<GroupDefinition> get() = groupCache ?: groupMap.values.toList().also { groupCache = it }
    val categories: List<CategoryDefinition> get() = categoryCache ?: categoryMap.values.toList().also { categoryCache = it }
    val features: List<FeatureDefinition> get() = featureCache ?: featureMap.values.toList().also { featureCache = it }

    fun group(id: String) = groupMap[id]
    fun category(id: String) = categoryMap[id]
    fun feature(id: String) = featureMap[id]
    fun categoriesIn(groupId: String) = categoriesInCache.getOrPut(groupId) { categoryMap.values.filter { it.groupId == groupId } }
    fun featuresIn(categoryId: String) = byCategoryCache.getOrPut(categoryId) { featureMap.values.filter { it.categoryId == categoryId } }

    fun registerGroup(group: GroupDefinition) {
        require(groupMap.putIfAbsent(group.id, group) == null) { "Duplicate group '${group.id}'" }
        invalidate()
    }

    fun registerCategory(category: CategoryDefinition) {
        require(category.groupId in groupMap) { "Category '${category.id}' references unknown group '${category.groupId}'" }
        require(categoryMap.putIfAbsent(category.id, category) == null) { "Duplicate category '${category.id}'" }
        invalidate()
    }

    fun registerFeature(feature: FeatureDefinition) {
        require(feature.categoryId in categoryMap) { "Feature '${feature.id}' references unknown category '${feature.categoryId}'" }
        require(featureMap.putIfAbsent(feature.id, feature) == null) { "Duplicate feature '${feature.id}'" }
        invalidate()
    }

    fun clear() {
        featureMap.clear(); categoryMap.clear(); groupMap.clear()
        invalidate()
    }

    /** Returns human-readable schema problems; an empty list means the registry is consistent. */
    fun validate(): List<String> {
        val problems = ArrayList<String>()
        for (f in featureMap.values) {
            val ids = HashSet<String>()
            for (s in f.settings) if (!ids.add(s.id)) problems += "${f.id}: duplicate setting '${s.id}'"
            for (d in f.dependencies) {
                val dep = featureMap[d]
                when {
                    dep == null -> problems += "${f.id}: unknown dependency '$d'"
                    dep.global != f.global && f.global -> problems += "${f.id}: global feature depends on profile feature '$d'"
                }
            }
            if (f.status == FeatureStatus.IMPLEMENTED && !f.toggleable && !f.defaultEnabled) problems += "${f.id}: non-toggleable feature must be enabled by default"
            if (f.status != FeatureStatus.IMPLEMENTED && f.defaultEnabled) problems += "${f.id}: unimplemented feature enabled by default"
            if (f.status != FeatureStatus.IMPLEMENTED && f.factory != null) problems += "${f.id}: unimplemented feature has a factory"
        }
        for (f in featureMap.values) if (hasCycle(f.id, HashSet())) problems += "${f.id}: dependency cycle"
        return problems
    }

    private fun hasCycle(id: String, path: MutableSet<String>): Boolean {
        if (!path.add(id)) return true
        val deps = featureMap[id]?.dependencies ?: return false
        val cyc = deps.any { hasCycle(it, path) }
        path.remove(id)
        return cyc
    }

    /** Features that (transitively) depend on [id]. */
    fun dependantsOf(id: String): List<FeatureDefinition> {
        val out = LinkedHashSet<FeatureDefinition>()
        fun walk(target: String) {
            for (f in featureMap.values) if (target in f.dependencies && out.add(f)) walk(f.id)
        }
        walk(id)
        return out.toList()
    }

    /** Case-insensitive match over translated title, description and id. */
    fun search(query: String, translate: (String) -> String): List<FeatureDefinition> {
        val terms = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return featureMap.values.filter { f ->
            val haystack = (translate(f.titleKey) + ' ' + translate(f.descriptionKey) + ' ' + f.id).lowercase()
            terms.all { it in haystack }
        }
    }
}
