package org.janelia.saalfeldlab.paintera.conversion

import org.janelia.saalfeldlab.n5.DataType
import org.janelia.saalfeldlab.n5.DatasetAttributes
import org.janelia.saalfeldlab.paintera.conversion.to.paintera.CHANNEL_IDENTIFIER
import org.janelia.saalfeldlab.paintera.conversion.to.paintera.LABEL_IDENTIFIER
import org.janelia.saalfeldlab.paintera.conversion.to.paintera.RAW_IDENTIFIER
import org.janelia.saalfeldlab.paintera.conversion.to.paintera.n5Reader
import org.janelia.saalfeldlab.paintera.conversion.to.paintera.n5Writer
import java.io.File
import java.io.Serializable

data class DatasetInfo(
        val inputContainer: String,
        val inputDataset: String,
        val outputContainer: String,
        val outputGroup: String = inputDataset) : Serializable {
    init {
        require(inputContainer != outputContainer) { "Input container $inputContainer is the same as output container $outputContainer" }
    }

    val type: String
        get() {
            val attrs = attributes
            return if (attrs.numDimensions == 4)
                CHANNEL_IDENTIFIER
            else when (inputContainer.n5Reader().getDatasetAttributes(inputDataset).dataType) {
                DataType.UINT64, DataType.INT64, DataType.UINT32 -> LABEL_IDENTIFIER
                else -> RAW_IDENTIFIER
            }
        }

    val attributes: DatasetAttributes
        get() = inputContainer.n5Reader().getDatasetAttributes(inputDataset)

    @Throws(InvalidInputContainer::class, InvalidInputDataset::class)
    fun ensureInput(): Boolean {
        if (!File(inputContainer).exists())
            throw InputContainerDoesNotExist(inputContainer)
        inputContainer.n5Reader().let { container ->
            if (!container.exists(inputDataset))
                throw InputDatasetDoesNotExist(inputContainer, inputDataset)
            if (!container.datasetExists(inputDataset))
                throw InputDatasetExistsButIsGroup(inputContainer, inputDataset)
            // TODO deal with illegal dimensionsalities
//            container.getDatasetAttributes(inputDataset).let { attrs ->
//                if (!legalDimensions.contains(attrs.numDimensions))
//                    throw IncompatibleNumDimensions(info.inputContainer, info.inputDataset, attrs.numDimensions, legalDimensions, type)
//            }
        }
        return true
    }

    @Throws(InvalidInputContainer::class, InvalidInputDataset::class)
    fun ensureOutput(existOk: Boolean): Boolean {
        if (File(outputContainer).let { it.exists() && !it.isDirectory })
            throw OutputContainerIsFile(outputContainer)
        if (!existOk && outputContainer.n5Writer().exists(outputGroup))
            throw OutputDatasetExists(outputContainer, outputGroup)
        return true
    }
}