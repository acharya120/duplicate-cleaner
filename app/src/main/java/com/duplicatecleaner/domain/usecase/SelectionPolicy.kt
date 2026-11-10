package com.duplicatecleaner.domain.usecase

import com.duplicatecleaner.domain.model.DeletionPlan
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.DuplicateGroup

/**
 * Safety rules for deletion. The invariant: after deleting the selection, every group still has at
 * least one file. Evaluated across ALL groups, because a file can appear in an exact group and a similar group.
 */
object SelectionPolicy {

    /** Ids of groups whose every file is selected (i.e. the selection would remove the last copy). */
    fun violatingGroups(groups: List<DuplicateGroup>, selected: Set<String>): List<String> =
        groups.filter { g -> g.files.isNotEmpty() && g.files.all { it.id in selected } }.map { it.id }

    fun isSafe(groups: List<DuplicateGroup>, selected: Set<String>): Boolean =
        violatingGroups(groups, selected).isEmpty()

    /** True if adding [fileId] would still leave a surviving copy in every group. */
    fun canSelect(groups: List<DuplicateGroup>, selected: Set<String>, fileId: String): Boolean =
        isSafe(groups, selected + fileId)

    /** Explicit user action "keep this one, select the rest" for a single group. */
    fun selectAllExcept(group: DuplicateGroup, keeperId: String): Set<String> =
        group.files.map { it.id }.filter { it != keeperId }.toSet()

    /**
     * Only a suggestion shown in the UI (never applied silently): largest file, then oldest, then
     * the one with the shortest path.
     */
    fun suggestedKeeper(group: DuplicateGroup): DuplicateFile =
        group.files.sortedWith(
            compareByDescending<DuplicateFile> { it.sizeBytes }
                .thenBy { it.lastModified }
                .thenBy { it.location.length },
        ).first()

    fun buildPlan(groups: List<DuplicateGroup>, selected: Set<String>): DeletionPlan? {
        if (selected.isEmpty() || !isSafe(groups, selected)) return null
        val files = groups.flatMap { it.files }.filter { it.id in selected }.distinctBy { it.id }
        return DeletionPlan(files, files.sumOf { it.sizeBytes })
    }
}
