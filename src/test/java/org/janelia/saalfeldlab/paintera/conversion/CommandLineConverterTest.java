package org.janelia.saalfeldlab.conversion;


import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import org.janelia.saalfeldlab.label.spark.exception.InputSameAsOutput;
import org.janelia.saalfeldlab.label.spark.exception.InvalidDataType;
import org.janelia.saalfeldlab.label.spark.exception.InvalidDataset;
import org.janelia.saalfeldlab.label.spark.exception.InvalidN5Container;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.imglib2.N5LabelMultisets;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.junit.Assert;
import org.junit.Test;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.integer.UnsignedLongType;

public class CommandLineConverterTest {

    private static final long[] dimensions = {5, 4, 4};

    private static final int[] blockSize = {3, 3, 3};

    private static final String LABEL_SOURCE_DATASET = "volumes/labels-source";

    private static final RandomAccessibleInterval<UnsignedLongType> LABELS = ArrayImgs.unsignedLongs(
            new long[] {
                    5, 5, 5, 4, 4,
                    5, 5, 4, 4, 4,
                    5, 4, 4, 4, 4,
                    5, 4, 4, 4, 1,

                    5, 5, 4, 4, 4,
                    5, 4, 4, 4, 4,
                    5, 5, 4, 4, 4,
                    5, 5, 5, 1, 1,

                    4, 4, 4, 4, 4,
                    4, 4, 4, 4, 4,
                    5, 4, 4, 4, 4,
                    5, 5, 5, 5, 1,

                    4, 4, 4, 4, 4,
                    4, 4, 4, 4, 4,
                    5, 4, 4, 4, 4,
                    5, 5, 5, 5, 1
            },
            dimensions);

    private final String tmpDir;

    private final N5Writer container;

    public CommandLineConverterTest() throws IOException {
        this.tmpDir = Files.createTempDirectory("command-line-converter-test").toString();
        this.container = new N5FSWriter(tmpDir);
            container.createDataset(LABEL_SOURCE_DATASET, dimensions, blockSize, DataType.UINT64, new RawCompression());
        N5Utils.save(LABELS, container, LABEL_SOURCE_DATASET, blockSize, new RawCompression());
    }

    @SuppressWarnings("unchecked")
	@Test
    public void testWinnerTakesAll() throws IOException, InvalidDataType, InvalidDataset, InputSameAsOutput, ConverterException, InvalidN5Container {
        final String labelTargetDataset = "volumes/labels-winner-takes-all";
        // TODO set spark master from outside, e.g. travis or in pom.xml
        System.setProperty("spark.master", "local[1]");
        CommandLineConverter.run(
                "-d", String.format("%s,%s,label,%s", tmpDir, LABEL_SOURCE_DATASET, labelTargetDataset),
                "-s", "2",
                String.format("--outputN5=%s", tmpDir),
                "--winner-takes-all-downsampling",
                "-b", String.format("%s,%s,%s", blockSize[0], blockSize[1], blockSize[2])
        );

        Assert.assertTrue(container.exists(labelTargetDataset));
        Assert.assertTrue(container.exists(labelTargetDataset + "/data"));
        Assert.assertTrue(container.exists(labelTargetDataset + "/unique-labels"));
        Assert.assertTrue(container.exists(labelTargetDataset + "/label-to-block-mapping"));

        Assert.assertTrue(container.datasetExists(labelTargetDataset + "/data/s0"));
        Assert.assertTrue(container.datasetExists(labelTargetDataset + "/data/s1"));
        Assert.assertFalse(container.datasetExists(labelTargetDataset + "/data/s2"));

        Assert.assertEquals(5, (long) container.getAttribute(labelTargetDataset, "maxId", long.class));

        final DatasetAttributes attrsS0 = container.getDatasetAttributes(labelTargetDataset + "/data/s0");
        final DatasetAttributes attrsS1 = container.getDatasetAttributes(labelTargetDataset + "/data/s1");
        Assert.assertEquals(DataType.UINT64, attrsS0.getDataType());
        Assert.assertEquals(DataType.UINT64, attrsS1.getDataType());
        Assert.assertArrayEquals(blockSize, attrsS0.getBlockSize());
        Assert.assertArrayEquals(blockSize, attrsS1.getBlockSize());
        Assert.assertArrayEquals(dimensions, attrsS0.getDimensions());
        Assert.assertArrayEquals(Arrays.stream(dimensions).map(dimension -> dimension / 2).toArray(), attrsS1.getDimensions());

        LoopBuilder
                .setImages(LABELS, (RandomAccessibleInterval<UnsignedLongType>)N5Utils.open(container, labelTargetDataset + "/data/s0"))
                .forEachPixel((e, a) -> Assert.assertTrue(e.valueEquals(a)));

        final RandomAccessibleInterval<UnsignedLongType> s1 = ArrayImgs.unsignedLongs(new long[] {
                5, 4,
                5, 4,

                4, 4,
                5, 4},
                attrsS1.getDimensions());

        LoopBuilder
                .setImages(s1, (RandomAccessibleInterval<UnsignedLongType>)N5Utils.open(container, labelTargetDataset + "/data/s1"))
                .forEachPixel((e, a) -> Assert.assertTrue(e.valueEquals(a)));
    }

    @Test
    public void testLabelMultisets() throws IOException, InvalidDataType, InvalidDataset, InputSameAsOutput, ConverterException, InvalidN5Container {
        final String labelTargetDataset = "volumes/labels-converted";
        // TODO set spark master from outside, e.g. travis or in pom.xml
        System.setProperty("spark.master", "local[1]");
        CommandLineConverter.run(
                "-d", String.format("%s,%s,label,%s", tmpDir, LABEL_SOURCE_DATASET, labelTargetDataset),
                "-s", "2",
                String.format("--outputN5=%s", tmpDir),
                "-b", String.format("%s,%s,%s", blockSize[0], blockSize[1], blockSize[2])
        );

        Assert.assertTrue(container.exists(labelTargetDataset));
        Assert.assertTrue(container.exists(labelTargetDataset + "/data"));
        Assert.assertTrue(container.exists(labelTargetDataset + "/unique-labels"));
        Assert.assertTrue(container.exists(labelTargetDataset + "/label-to-block-mapping"));

        Assert.assertTrue(container.datasetExists(labelTargetDataset + "/data/s0"));
        Assert.assertTrue(container.datasetExists(labelTargetDataset + "/data/s1"));
        Assert.assertFalse(container.datasetExists(labelTargetDataset + "/data/s2"));

        Assert.assertEquals(5, (long) container.getAttribute(labelTargetDataset, "maxId", long.class));

        final DatasetAttributes attrsS0 = container.getDatasetAttributes(labelTargetDataset + "/data/s0");
        final DatasetAttributes attrsS1 = container.getDatasetAttributes(labelTargetDataset + "/data/s1");
        Assert.assertEquals(DataType.UINT8, attrsS0.getDataType());
        Assert.assertEquals(DataType.UINT8, attrsS1.getDataType());
        Assert.assertTrue(CommandLineConverter.isLabelDataType(container, labelTargetDataset + "/data/s0"));
        Assert.assertTrue(CommandLineConverter.isLabelDataType(container, labelTargetDataset + "/data/s1"));
        Assert.assertArrayEquals(blockSize, attrsS0.getBlockSize());
        Assert.assertArrayEquals(blockSize, attrsS1.getBlockSize());
        Assert.assertArrayEquals(dimensions, attrsS0.getDimensions());

        // FIXME: Should have the same dimensions as in the winner-takes-all case? Currently it's 1px more if input size is an odd number
        Assert.assertArrayEquals(Arrays.stream(dimensions).map(dimension -> dimension / 2 + (dimension % 2 != 0 ? 1 : 0)).toArray(), attrsS1.getDimensions());

        LoopBuilder
                .setImages(LABELS, N5LabelMultisets.openLabelMultiset(container, labelTargetDataset + "/data/s0"))
                .forEachPixel((e, a) ->
                	Assert.assertTrue(a.entrySet().size() == 1 && a.entrySet().iterator().next().getElement().id() == e.get())
            	);

        final RandomAccessibleInterval<UnsignedLongType> s1ArgMax = ArrayImgs.unsignedLongs(new long[] {
                5, 4, 4,
                5, 4, 1,

                4, 4, 4,
                5, 4, 1},
                attrsS1.getDimensions());

        LoopBuilder
		        .setImages(s1ArgMax, N5LabelMultisets.openLabelMultiset(container, labelTargetDataset + "/data/s1"))
		        .forEachPixel((e, a) ->
		        	Assert.assertEquals(e.get(), a.argMax()));
    }
}