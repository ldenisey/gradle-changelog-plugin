// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.changelog.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.ChangelogPluginConstants.ATX_2
import org.jetbrains.changelog.ChangelogPluginConstants.ATX_3
import org.jetbrains.changelog.ChangelogPluginConstants.NEW_LINE
import org.jetbrains.changelog.exceptions.MissingReleaseNoteException
import org.jetbrains.changelog.reformat

abstract class PatchChangelogTask : DefaultTask() {

    @get:Input
    @get:Optional
    @Option(option = "release-note", description = "Custom release note content")
    var releaseNote: String? = null

    @get:InputFile
    @get:Optional
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val outputFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val groups: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val header: Property<String>

    @get:Input
    @get:Optional
    abstract val headerParserRegex: Property<Regex>

    @get:Input
    @get:Optional
    abstract val preTitle: Property<String>

    @get:Input
    @get:Optional
    abstract val title: Property<String>

    @get:Input
    @get:Optional
    abstract val introduction: Property<String>

    @get:Input
    @get:Optional
    abstract val itemPrefix: Property<String>

    @get:Input
    @get:Optional
    abstract val keepUnreleasedSection: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val patchEmpty: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val unreleasedTerm: Property<String>

    @get:Input
    @get:Optional
    abstract val version: Property<String>

    @TaskAction
    fun run() {
        val unreleasedTermValue = unreleasedTerm.get()

        val changelog = Changelog(
            inputFile.get().asFile,
            preTitle.orNull,
            title.orNull,
            introduction.orNull,
            unreleasedTerm.get(),
            headerParserRegex.get(),
            itemPrefix.get(),
        )

        val preTitleValue = preTitle.orNull ?: changelog.preTitleValue
        val titleValue = title.orNull ?: changelog.titleValue
        val introductionValue = introduction.orNull ?: changelog.introductionValue
        val headerValue = header.get()

        val item = changelog.runCatching { get(unreleasedTermValue) }.getOrNull()
        val otherItems = changelog.getAll().filterNot { it.key == unreleasedTermValue }.values
        val noUnreleasedSection = item == null || item.getSections().isEmpty()
        val noReleaseNote = releaseNote.isNullOrBlank()
        val content = releaseNote ?: item?.withHeader(false)?.toText() ?: ""

        if (patchEmpty.get() && content.isEmpty()) {
            logger.info(":patchChangelog task skipped due to the missing release note in the '$unreleasedTerm'.")
            throw StopActionException()
        }

        if (noUnreleasedSection && noReleaseNote && content.isBlank()) {
            throw MissingReleaseNoteException(
                ":patchChangelog task requires release note to be provided. " +
                        "Add '$ATX_2 $unreleasedTermValue' section header to your changelog file: " +
                        "'${inputFile.get().asFile.canonicalPath}' or provide it using '--release-note' CLI option."
            )
        }

        sequence {
            if (preTitleValue.isNotEmpty()) {
                yield(preTitleValue)
                yield(NEW_LINE)
            }
            if (titleValue.isNotEmpty()) {
                yield(titleValue)
                yield(NEW_LINE)
            }
            if (introductionValue.isNotEmpty()) {
                yield(introductionValue)
                yield(NEW_LINE)
            }

            if (keepUnreleasedSection.get()) {
                yield("$ATX_2 $unreleasedTermValue")
                yield(NEW_LINE)

                groups.get()
                    .map { "$ATX_3 $it" }
                    .let { yieldAll(it) }
            }

            if (item != null) {
                yield("$ATX_2 $headerValue")

                if (content.isNotBlank()) {
                    yield(content)
                } else {
                    yield(item.withHeader(false))
                }
            }

            yield(NEW_LINE)
            yieldAll(otherItems)
        }
            .joinToString(NEW_LINE)
            .reformat()
            .let {
                outputFile.get().asFile.writeText(it)
            }
    }
}
