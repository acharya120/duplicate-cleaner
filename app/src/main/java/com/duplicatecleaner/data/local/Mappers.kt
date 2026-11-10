package com.duplicatecleaner.data.local

import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.DuplicateGroup
import com.duplicatecleaner.domain.model.FileCategory
import com.duplicatecleaner.domain.model.FileSource
import com.duplicatecleaner.domain.model.GroupType

private const val RS = '\u001E'
private const val US = '\u001F'

fun DuplicateFile.toEntity(memberships: String = "") = DuplicateFileEntity(
    uri, name, mimeType, sizeBytes, lastModified, location, category.name, source.name,
    physicalKey, width, height, sha256, perceptualHash, memberships,
)

fun DuplicateFileEntity.toDomain() = DuplicateFile(
    uri = uri, name = displayName, mimeType = mimeType, sizeBytes = sizeBytes, lastModified = lastModified,
    location = location, category = FileCategory.valueOf(category), source = FileSource.valueOf(source),
    physicalKey = physicalKey, width = width, height = height, sha256 = sha256, perceptualHash = perceptualHash,
)

/** uri -> encoded group memberships. */
fun membershipsOf(groups: List<DuplicateGroup>): Map<String, String> {
    val map = HashMap<String, StringBuilder>()
    for (g in groups) for (f in g.files) {
        val sb = map.getOrPut(f.uri) { StringBuilder() }
        if (sb.isNotEmpty()) sb.append(RS)
        sb.append(g.id).append(US).append(g.type.name)
    }
    return map.mapValues { it.value.toString() }
}

fun rebuildGroups(entities: List<DuplicateFileEntity>): List<DuplicateGroup> {
    val byGroup = HashMap<Pair<String, GroupType>, MutableList<DuplicateFile>>()
    for (e in entities) {
        val file = e.toDomain()
        e.memberships.split(RS).filter { it.isNotEmpty() }.forEach { rec ->
            val parts = rec.split(US)
            if (parts.size == 2) {
                val type = runCatching { GroupType.valueOf(parts[1]) }.getOrNull() ?: return@forEach
                byGroup.getOrPut(parts[0] to type) { ArrayList() }.add(file)
            }
        }
    }
    return byGroup.filter { it.value.size >= 2 }
        .map { (k, files) -> DuplicateGroup(k.first, k.second, files.sortedBy { it.lastModified }) }
        .sortedByDescending { it.recoverableBytes }
}
