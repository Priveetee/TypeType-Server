package dev.typetype.server.services

import dev.typetype.server.models.SearchFilterOption
import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterItem

internal fun Filter?.toSearchFilterOptions(): List<SearchFilterOption> = this?.filterGroups
    ?.flatMap { group -> group.filterItems.map { item -> item.toSearchFilterOption(group.groupName.orEmpty()) } }
    ?: emptyList()

internal fun Filter?.findSearchFilter(value: String?): List<FilterItem> = value?.let { raw ->
    this?.filterGroups
        ?.flatMap { group -> group.filterItems.map { group.groupName.orEmpty() to it } }
        ?.firstOrNull { (groupName, item) -> item.searchFilterValue(groupName) == raw }
        ?.let { listOf(it.second) }
} ?: emptyList()

internal fun Filter?.defaultSearchFilter(): List<FilterItem> = this?.filterGroups
    ?.firstOrNull()
    ?.filterItems
    ?.firstOrNull()
    ?.let { listOf(it) }
    ?: emptyList()

private fun FilterItem.toSearchFilterOption(groupName: String): SearchFilterOption = SearchFilterOption(
    value = searchFilterValue(groupName),
    label = if (groupName.isBlank()) name else "$groupName: $name",
)

private fun FilterItem.searchFilterValue(groupName: String): String =
    listOf(groupName, identifier.toString(), name).joinToString("|")
