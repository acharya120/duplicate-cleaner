package com.duplicatecleaner

import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.FileCategory
import com.duplicatecleaner.domain.model.FileSource

fun testFile(
    uri: String,
    name: String = "$uri.jpg",
    size: Long = 100,
    sha: String? = null,
    phash: Long? = null,
    category: FileCategory = FileCategory.PHOTO,
    modified: Long = 0,
    w: Int = 0,
    h: Int = 0,
    key: String? = null,
) = DuplicateFile(
    uri = uri, name = name, mimeType = "image/jpeg", sizeBytes = size, lastModified = modified,
    location = "DCIM/", category = category, source = FileSource.MEDIASTORE, physicalKey = key,
    width = w, height = h, sha256 = sha, perceptualHash = phash,
)
