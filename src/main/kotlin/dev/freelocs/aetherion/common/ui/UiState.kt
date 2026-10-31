package dev.freelocs.aetherion.common.ui

/**
 * Pure UI model shared by all renderers: what is selected, searched and expanded. It holds no
 * Minecraft types and no drawing state, so it can be unit-tested and reused by any screen.
 */
class UiState {
    /** Incremented whenever the visible page content must be rebuilt (navigation, search, language, profile). */
    var version = 0
        private set

    var categoryId = "general"
        private set
    var selectedFeatureId: String? = null
        private set
    var search = ""
        private set
    var roadmapTab = false
        private set
    val expanded = HashSet<String>()
    val collapsedGroups = HashSet<String>()
    var sidebarCollapsed = false

    val searching get() = search.isNotBlank()

    fun invalidate() { version++ }

    fun openCategory(id: String) {
        if (id == categoryId && !searching) return
        categoryId = id
        search = ""
        selectedFeatureId = null
        roadmapTab = false
        version++
    }

    fun openFeature(categoryId: String, featureId: String) {
        this.categoryId = categoryId
        search = ""
        selectedFeatureId = featureId
        expanded += featureId
        version++
    }

    fun selectFeature(id: String?) {
        if (selectedFeatureId == id) return
        selectedFeatureId = id
    }

    fun setSearch(query: String) {
        if (query == search) return
        search = query
        version++
    }

    fun setRoadmapTab(roadmap: Boolean) {
        if (roadmapTab == roadmap) return
        roadmapTab = roadmap
        version++
    }

    fun toggleExpanded(id: String): Boolean = if (!expanded.remove(id)) { expanded += id; true } else false

    fun toggleGroup(id: String) { if (!collapsedGroups.remove(id)) collapsedGroups += id }
}
