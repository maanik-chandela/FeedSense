package com.example.feedsense.analysis.ml.preprocess.golden

import com.example.feedsense.analysis.ml.preprocess.*

// --------------------------------
// GOLDEN FIXTURE DEFINITIONS (8B-15-4 §4, §5, §14-§20)
// --------------------------------
//
// The complete initial fixture corpus. Each fixture is a
// deterministic reference case with expected outputs.
//
// Categories:
//   BASIC_IMAGE  - fundamental image types
//   ORIENTATION  - rotation correctness
//   DIMENSION    - edge-case sizes
//   PIXEL_PATTERN - synthetic patterns
//   CHANNEL_ORDER - RGB/BGR correctness
//   ALPHA        - alpha handling policy
//   RESIZE_CROP_PADDING - spatial transforms
//   NORMALIZATION - value normalization
//   DATATYPE_LAYOUT - tensor type and layout
//   PRIVACY      - privacy boundary enforcement
//   NEGATIVE     - expected failures
//
// Fixtures use the default (nearest-neighbor, RESIZE) config
// unless a configOverride is specified.

/**
 * All golden fixture definitions for the initial corpus.
 */
object GoldenFixtureDefinitions {

    private val DEFAULT_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 4,
        inputHeight = 4,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.RESIZE,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    /**
     * The canonical default configuration used by fixtures that do
     * not override it (4x4, NEAREST, RGB, FLOAT32, NHWC).
     */
    fun allFixturesConfig(): PreprocessingConfig = DEFAULT_CONFIG

    private val REFERENCE_CONFIG = PreprocessingConfig.mobileNetV4ConvReference()

    private val CENTER_CROP_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 4,
        inputHeight = 4,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.CENTER_CROP,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val PAD_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 4,
        inputHeight = 4,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.PAD,
        paddingPolicy = PaddingPolicy.PAD_BLACK,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val BILINEAR_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 4,
        inputHeight = 4,
        resizePolicy = ResizePolicy.BILINEAR,
        cropPolicy = AspectRatioPolicy.CENTER_CROP,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.BILINEAR,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val SCALE_ONLY_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 2,
        inputHeight = 2,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.RESIZE,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        mean = emptyList(),
        std = emptyList(),
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val NORMALIZED_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 2,
        inputHeight = 2,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.RESIZE,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        mean = listOf(0.485, 0.456, 0.406),
        std = listOf(0.229, 0.224, 0.225),
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val BGR_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 2,
        inputHeight = 2,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.RESIZE,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.BGR,
        channelOrder = PreprocessChannelOrder.BGR,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val RGBA_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 2,
        inputHeight = 2,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.RESIZE,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGBA,
        channelOrder = PreprocessChannelOrder.RGBA,
        alphaPolicy = AlphaPolicy.PRESERVE,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val COMPOSITE_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 2,
        inputHeight = 2,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.RESIZE,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.COMPOSITE,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val GRAYSCALE_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 2,
        inputHeight = 2,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.RESIZE,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.GRAYSCALE,
        channelOrder = PreprocessChannelOrder.GRAYSCALE,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0,
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val INT8_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 2,
        inputHeight = 2,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.RESIZE,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.INT8,
        scale = 1.0 / 127.0,
        zeroPoint = 0.0,
        mean = emptyList(),
        std = emptyList(),
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    private val UINT8_CONFIG = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 2,
        inputHeight = 2,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = AspectRatioPolicy.RESIZE,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.UINT8,
        scale = 1.0 / 255.0,
        zeroPoint = 0.0,
        mean = emptyList(),
        std = emptyList(),
        tensorLayout = PreprocessingTensorLayout.NHWC,
        batchSize = BatchHandling.BATCH_1
    )

    // -------------------------------------------------------------------
    // BASIC IMAGE FIXTURES
    // -------------------------------------------------------------------

    val ALL_BLACK_4x4 = GoldenFixture(
        fixtureId = "basic-all-black-4x4",
        description = "4x4 source all-black, RESIZE to 2x2, scale-only normalization",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 4,
        sourceHeight = 4,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.BASIC_IMAGE,
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedLayout = "NHWC",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("basic-all-black-4x4"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("basic-all-black-4x4"),
            description = "All zeros after scale [0,1]"
        )
    )

    val ALL_WHITE_4x4 = GoldenFixture(
        fixtureId = "basic-all-white-4x4",
        description = "4x4 source all-white, RESIZE to 2x2, scale-only normalization",
        sourcePattern = SourcePattern.ALL_WHITE,
        sourceWidth = 4,
        sourceHeight = 4,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.BASIC_IMAGE,
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedLayout = "NHWC",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("basic-all-white-4x4"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("basic-all-white-4x4"),
            description = "All 1.0 after scale [0,1]"
        )
    )

    val PORTRAIT_6x8 = GoldenFixture(
        fixtureId = "basic-portrait-6x8",
        description = "6x8 portrait image resized to 4x4, RESIZE policy",
        sourcePattern = SourcePattern.PORTRAIT_STRIP,
        sourceWidth = 6,
        sourceHeight = 8,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.BASIC_IMAGE,
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Portrait resized to square"
        )
    )

    val LANDSCAPE_8x6 = GoldenFixture(
        fixtureId = "basic-landscape-8x6",
        description = "8x6 landscape image resized to 4x4, RESIZE policy",
        sourcePattern = SourcePattern.LANDSCAPE_STRIP,
        sourceWidth = 8,
        sourceHeight = 6,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.BASIC_IMAGE,
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Landscape resized to square"
        )
    )

    val SQUARE_4x4 = GoldenFixture(
        fixtureId = "basic-square-4x4",
        description = "4x4 already-target-size square image",
        sourcePattern = SourcePattern.KNOWN_GRADIENT,
        sourceWidth = 4,
        sourceHeight = 4,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.BASIC_IMAGE,
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Already target size, no resize needed"
        )
    )

    // -------------------------------------------------------------------
    // ORIENTATION FIXTURES (§14)
    // -------------------------------------------------------------------

    val ORIENTATION_0 = GoldenFixture(
        fixtureId = "orient-0-degrees",
        description = "4x3 channel-isolation frame at 0 degrees",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 4,
        sourceHeight = 3,
        sourceOrientation = FrameOrientation.DEG_0,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.ORIENTATION,
        tags = listOf("orientation", "rotation"),
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("orient-0-degrees"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("orient-0-degrees"),
            description = "0 degrees = identity rotation"
        )
    )

    val ORIENTATION_90 = GoldenFixture(
        fixtureId = "orient-90-degrees",
        description = "4x3 channel-isolation frame captured at 90 degrees CW",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 4,
        sourceHeight = 3,
        sourceOrientation = FrameOrientation.DEG_90,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.ORIENTATION,
        tags = listOf("orientation", "rotation"),
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("orient-90-degrees"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("orient-90-degrees"),
            description = "90 degrees = 3 CW quarter turns to normalize"
        )
    )

    val ORIENTATION_180 = GoldenFixture(
        fixtureId = "orient-180-degrees",
        description = "4x3 channel-isolation frame captured at 180 degrees",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 4,
        sourceHeight = 3,
        sourceOrientation = FrameOrientation.DEG_180,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.ORIENTATION,
        tags = listOf("orientation", "rotation"),
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("orient-180-degrees"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("orient-180-degrees"),
            description = "180 degrees = 2 CW quarter turns to normalize"
        )
    )

    val ORIENTATION_270 = GoldenFixture(
        fixtureId = "orient-270-degrees",
        description = "4x3 channel-isolation frame captured at 270 degrees CW",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 4,
        sourceHeight = 3,
        sourceOrientation = FrameOrientation.DEG_270,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.ORIENTATION,
        tags = listOf("orientation", "rotation"),
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("orient-270-degrees"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("orient-270-degrees"),
            description = "270 degrees = 1 CW quarter turn to normalize"
        )
    )

    // -------------------------------------------------------------------
    // DIMENSION FIXTURES
    // -------------------------------------------------------------------

    val DIM_1x1 = GoldenFixture(
        fixtureId = "dim-1x1",
        description = "1x1 source resized to 4x4",
        sourcePattern = SourcePattern.ONE_BY_ONE,
        sourceWidth = 1,
        sourceHeight = 1,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.DIMENSION,
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Smallest possible image scaled up"
        )
    )

    val DIM_2x2_VERY_SMALL = GoldenFixture(
        fixtureId = "dim-2x2-very-small",
        description = "2x2 source with distinct colors resized to 4x4",
        sourcePattern = SourcePattern.VERY_SMALL,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.DIMENSION,
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Very small upscaled with nearest neighbor"
        )
    )

    val DIM_ODD_5x7 = GoldenFixture(
        fixtureId = "dim-odd-5x7",
        description = "5x7 odd-dimension source resized to 4x4",
        sourcePattern = SourcePattern.ODD_DIMENSIONS,
        sourceWidth = 5,
        sourceHeight = 7,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.DIMENSION,
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Odd dimensions resized to even"
        )
    )

    val DIM_LARGER_THAN_TARGET = GoldenFixture(
        fixtureId = "dim-larger-than-target",
        description = "16x16 source (larger than 4x4 target)",
        sourcePattern = SourcePattern.LARGER_THAN_TARGET,
        sourceWidth = 16,
        sourceHeight = 16,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.DIMENSION,
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Downsized from larger source"
        )
    )

    val DIM_SMALLER_THAN_TARGET = GoldenFixture(
        fixtureId = "dim-smaller-than-target",
        description = "2x2 source smaller than 4x4 target",
        sourcePattern = SourcePattern.SMALLER_THAN_TARGET,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = DEFAULT_CONFIG,
        category = FixtureCategory.DIMENSION,
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Upscaled from smaller source"
        )
    )

    // -------------------------------------------------------------------
    // PIXEL PATTERN FIXTURES
    // -------------------------------------------------------------------

    val PATTERN_CHANNEL_ISOLATION = GoldenFixture(
        fixtureId = "pattern-rgb-channel-isolation",
        description = "4x4 RGB channel-isolation: R=x*80, G=y*80, B=(x+y)*40",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 4,
        sourceHeight = 4,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PIXEL_PATTERN,
        tags = listOf("channel-order", "rgb-bgr"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("pattern-rgb-channel-isolation"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("pattern-rgb-channel-isolation"),
            description = "Channel isolation pattern: each channel encodes position"
        )
    )

    val PATTERN_HIGH_CONTRAST = GoldenFixture(
        fixtureId = "pattern-high-contrast-edges",
        description = "4x4 checkerboard of black/white (cell=1)",
        sourcePattern = SourcePattern.HIGH_CONTRAST_EDGES,
        sourceWidth = 4,
        sourceHeight = 4,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PIXEL_PATTERN,
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("pattern-high-contrast-edges"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("pattern-high-contrast-edges"),
            description = "Maximum contrast at every pixel boundary"
        )
    )

    val PATTERN_GRADIENT = GoldenFixture(
        fixtureId = "pattern-known-gradient",
        description = "4x4 known gradient: R=x*255/w, G=y*255/h, B=(x+y)*128/(w+h)",
        sourcePattern = SourcePattern.KNOWN_GRADIENT,
        sourceWidth = 4,
        sourceHeight = 4,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PIXEL_PATTERN,
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("pattern-known-gradient"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("pattern-known-gradient"),
            description = "Known gradient with computable expected values"
        )
    )

    val PATTERN_GRAYSCALE_LIKE = GoldenFixture(
        fixtureId = "pattern-grayscale-like",
        description = "4x4 grayscale-like pattern (R=G=B at each pixel)",
        sourcePattern = SourcePattern.GRAYSCALE_LIKE,
        sourceWidth = 4,
        sourceHeight = 4,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PIXEL_PATTERN,
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("pattern-grayscale-like"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("pattern-grayscale-like"),
            description = "Grayscale-like: R=G=B, useful for detecting channel duplication"
        )
    )

    // -------------------------------------------------------------------
    // CHANNEL ORDER FIXTURES (§15)
    // -------------------------------------------------------------------

    val CHANNEL_RGB = GoldenFixture(
        fixtureId = "channel-rgb-order",
        description = "2x2 channel-isolation in RGB order",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.CHANNEL_ORDER,
        tags = listOf("channel-order", "rgb"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("channel-rgb-order"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("channel-rgb-order"),
            description = "RGB: channels in R,G,B order"
        )
    )

    val CHANNEL_BGR = GoldenFixture(
        fixtureId = "channel-bgr-order",
        description = "2x2 channel-isolation in BGR order",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = BGR_CONFIG,
        category = FixtureCategory.CHANNEL_ORDER,
        tags = listOf("channel-order", "bgr"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("channel-bgr-order"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("channel-bgr-order"),
            description = "BGR: channels in B,G,R order, opposite of RGB"
        )
    )

    // -------------------------------------------------------------------
    // ALPHA FIXTURES (§16)
    // -------------------------------------------------------------------

    val ALPHA_DISCARD_OPAQUE = GoldenFixture(
        fixtureId = "alpha-discard-opaque",
        description = "2x2 opaque pixels with DISCARD alpha policy",
        sourcePattern = SourcePattern.RED_DOMINANT,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.ALPHA,
        tags = listOf("alpha", "discard"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Opaque red, alpha discarded -> pure red RGB"
        )
    )

    val ALPHA_DISCARD_TRANSLUCENT = GoldenFixture(
        fixtureId = "alpha-discard-translucent",
        description = "2x2 translucent red with DISCARD alpha policy",
        sourcePattern = SourcePattern.TRANSLUCENT,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.ALPHA,
        tags = listOf("alpha", "discard"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Translucent red with DISCARD: alpha ignored, pure red RGB"
        )
    )

    val ALPHA_COMPOSITE = GoldenFixture(
        fixtureId = "alpha-composite-blend",
        description = "2x2 semi-transparent with COMPOSITE against white background",
        sourcePattern = SourcePattern.SEMI_TRANSPARENT,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = COMPOSITE_CONFIG,
        category = FixtureCategory.ALPHA,
        tags = listOf("alpha", "composite"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Alpha composited against white background"
        )
    )

    val ALPHA_PRESERVE_RGBA = GoldenFixture(
        fixtureId = "alpha-preserve-rgba",
        description = "2x2 opaque pixels with PRESERVE alpha -> 4-channel RGBA",
        sourcePattern = SourcePattern.RED_DOMINANT,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = RGBA_CONFIG,
        category = FixtureCategory.ALPHA,
        tags = listOf("alpha", "preserve", "rgba"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 4,
            expectedTensorType = "FLOAT32",
            description = "RGBA: alpha preserved as first channel"
        )
    )

    // -------------------------------------------------------------------
    // RESIZE/CROP/PADDING FIXTURES (§17)
    // -------------------------------------------------------------------

    val RESIZE_PORTRAIT_TO_SQUARE = GoldenFixture(
        fixtureId = "resize-portrait-to-square",
        description = "3x6 portrait center-cropped and resized to 4x4",
        sourcePattern = SourcePattern.PORTRAIT_STRIP,
        sourceWidth = 3,
        sourceHeight = 6,
        configOverride = CENTER_CROP_CONFIG,
        category = FixtureCategory.RESIZE_CROP_PADDING,
        tags = listOf("resize", "center-crop"),
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Portrait source center-cropped to square"
        )
    )

    val RESIZE_LANDSCAPE_TO_SQUARE = GoldenFixture(
        fixtureId = "resize-landscape-to-square",
        description = "6x3 landscape center-cropped and resized to 4x4",
        sourcePattern = SourcePattern.LANDSCAPE_STRIP,
        sourceWidth = 6,
        sourceHeight = 3,
        configOverride = CENTER_CROP_CONFIG,
        category = FixtureCategory.RESIZE_CROP_PADDING,
        tags = listOf("resize", "center-crop"),
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Landscape source center-cropped to square"
        )
    )

    val PAD_WIDE_TO_SQUARE = GoldenFixture(
        fixtureId = "pad-wide-to-square",
        description = "4x2 wide frame padded to 4x4 with black bars",
        sourcePattern = SourcePattern.LANDSCAPE_STRIP,
        sourceWidth = 4,
        sourceHeight = 2,
        configOverride = PAD_CONFIG,
        category = FixtureCategory.RESIZE_CROP_PADDING,
        tags = listOf("resize", "pad"),
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Wide frame padded with black bars to square"
        )
    )

    val PAD_TALL_TO_SQUARE = GoldenFixture(
        fixtureId = "pad-tall-to-square",
        description = "2x4 tall frame padded to 4x4 with black bars",
        sourcePattern = SourcePattern.PORTRAIT_STRIP,
        sourceWidth = 2,
        sourceHeight = 4,
        configOverride = PAD_CONFIG,
        category = FixtureCategory.RESIZE_CROP_PADDING,
        tags = listOf("resize", "pad"),
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Tall frame padded with black bars to square"
        )
    )

    val RESIZE_BILINEAR = GoldenFixture(
        fixtureId = "resize-bilinear-6to4",
        description = "6x6 to 4x4 with bilinear interpolation and center crop",
        sourcePattern = SourcePattern.KNOWN_GRADIENT,
        sourceWidth = 6,
        sourceHeight = 6,
        configOverride = BILINEAR_CONFIG,
        category = FixtureCategory.RESIZE_CROP_PADDING,
        tags = listOf("resize", "bilinear"),
        expectation = GoldenExpectation(
            expectedWidth = 4,
            expectedHeight = 4,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Bilinear interpolation from 6x6 to 4x4"
        )
    )

    // -------------------------------------------------------------------
    // NORMALIZATION FIXTURES (§18)
    // -------------------------------------------------------------------

    val NORM_SCALE_ONLY_BLACK = GoldenFixture(
        fixtureId = "norm-scale-only-black",
        description = "2x2 all-black with scale-only normalization (0/255 = 0.0)",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.NORMALIZATION,
        tags = listOf("normalization", "scale-only", "boundary"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("norm-scale-only-black"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("norm-scale-only-black"),
            description = "Black pixels -> all 0.0"
        )
    )

    val NORM_SCALE_ONLY_WHITE = GoldenFixture(
        fixtureId = "norm-scale-only-white",
        description = "2x2 all-white with scale-only normalization (255/255 = 1.0)",
        sourcePattern = SourcePattern.ALL_WHITE,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.NORMALIZATION,
        tags = listOf("normalization", "scale-only", "boundary"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("norm-scale-only-white"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("norm-scale-only-white"),
            description = "White pixels -> all 1.0"
        )
    )

    val NORM_STD_BLACK = GoldenFixture(
        fixtureId = "norm-std-black",
        description = "2x2 all-black with mean/std normalization",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = NORMALIZED_CONFIG,
        category = FixtureCategory.NORMALIZATION,
        tags = listOf("normalization", "mean-std", "boundary"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("norm-std-black"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("norm-std-black"),
            description = "Black: (0 - mean) / std per channel"
        )
    )

    val NORM_STD_WHITE = GoldenFixture(
        fixtureId = "norm-std-white",
        description = "2x2 all-white with mean/std normalization",
        sourcePattern = SourcePattern.ALL_WHITE,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = NORMALIZED_CONFIG,
        category = FixtureCategory.NORMALIZATION,
        tags = listOf("normalization", "mean-std", "boundary"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("norm-std-white"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("norm-std-white"),
            description = "White: (1 - mean) / std per channel"
        )
    )

    val NORM_RED_DOMINANT = GoldenFixture(
        fixtureId = "norm-red-dominant",
        description = "2x2 solid red with mean/std normalization (channel-specific values)",
        sourcePattern = SourcePattern.RED_DOMINANT,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = NORMALIZED_CONFIG,
        category = FixtureCategory.NORMALIZATION,
        tags = listOf("normalization", "mean-std", "channel-specific"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("norm-red-dominant"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("norm-red-dominant"),
            description = "Red: R=(1-0.485)/0.229, G=(0-0.456)/0.224, B=(0-0.406)/0.225"
        )
    )

    // -------------------------------------------------------------------
    // DATATYPE / LAYOUT FIXTURES (§19)
    // -------------------------------------------------------------------

    val DATATYPE_FLOAT32 = GoldenFixture(
        fixtureId = "datatype-float32",
        description = "2x2 RGB with FLOAT32 tensor type",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.DATATYPE_LAYOUT,
        tags = listOf("datatype", "float32", "nhwc"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedDatatype = "FLOAT32",
            description = "FLOAT32 tensor, no quantized bytes"
        )
    )

    val DATATYPE_INT8 = GoldenFixture(
        fixtureId = "datatype-int8",
        description = "2x2 RGB with INT8 tensor type",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = INT8_CONFIG,
        category = FixtureCategory.DATATYPE_LAYOUT,
        tags = listOf("datatype", "int8", "quantization"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "INT8",
            expectedDatatype = "INT8",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("datatype-int8"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("datatype-int8"),
            description = "INT8 quantized tensor with dequantized floats"
        )
    )

    val DATATYPE_UINT8 = GoldenFixture(
        fixtureId = "datatype-uint8",
        description = "2x2 RGB with UINT8 tensor type",
        sourcePattern = SourcePattern.RGB_CHANNEL_ISOLATION,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = UINT8_CONFIG,
        category = FixtureCategory.DATATYPE_LAYOUT,
        tags = listOf("datatype", "uint8", "quantization"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "UINT8",
            expectedDatatype = "UINT8",
            expectedTensorHash = GoldenHashRegistry.pinnedTensorHash("datatype-uint8"),
            expectedFingerprint = GoldenHashRegistry.pinnedFingerprint("datatype-uint8"),
            description = "UINT8 quantized tensor with dequantized floats"
        )
    )

    // -------------------------------------------------------------------
    // PRIVACY BOUNDARY FIXTURES (§5)
    // -------------------------------------------------------------------

    val PRIVACY_ACCEPTED_SANITIZED = GoldenFixture(
        fixtureId = "privacy-accepted-sanitized",
        description = "2x2 black image with SANITIZED privacy status",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PRIVACY,
        tags = listOf("privacy", "accepted", "sanitized"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedSanitizationStatus = "SANITIZED",
            description = "Sanitized evidence accepted for preprocessing"
        )
    )

    val PRIVACY_ACCEPTED_NOT_REQUIRED = GoldenFixture(
        fixtureId = "privacy-accepted-not-required",
        description = "2x2 black image with NOT_REQUIRED privacy status",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PRIVACY,
        tags = listOf("privacy", "accepted", "not-required"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedSanitizationStatus = "NOT_REQUIRED",
            description = "NOT_REQUIRED evidence accepted for preprocessing"
        )
    )

    val PRIVACY_REJECTED_FAILED = GoldenFixture(
        fixtureId = "privacy-rejected-failed",
        description = "Evidence with SANITIZATION_FAILED must be rejected",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PRIVACY,
        tags = listOf("privacy", "rejected", "failed"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedFailureCode = com.example.feedsense.analysis.ml.preprocess.PreprocessFailureCode.PRIVACY_REJECTED,
            description = "SANITIZATION_FAILED -> PRIVACY_REJECTED"
        )
    )

    val PRIVACY_REJECTED_UNAVAILABLE = GoldenFixture(
        fixtureId = "privacy-rejected-unavailable",
        description = "Evidence with SANITIZATION_UNAVAILABLE must be rejected",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PRIVACY,
        tags = listOf("privacy", "rejected", "unavailable"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedFailureCode = com.example.feedsense.analysis.ml.preprocess.PreprocessFailureCode.PRIVACY_REJECTED,
            description = "SANITIZATION_UNAVAILABLE -> PRIVACY_REJECTED"
        )
    )

    val PRIVACY_REJECTED_UNKNOWN = GoldenFixture(
        fixtureId = "privacy-rejected-unknown",
        description = "Evidence with UNKNOWN privacy status must be rejected",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PRIVACY,
        tags = listOf("privacy", "rejected", "unknown"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedFailureCode = com.example.feedsense.analysis.ml.preprocess.PreprocessFailureCode.PRIVACY_REJECTED,
            description = "UNKNOWN -> PRIVACY_REJECTED"
        )
    )

    // -------------------------------------------------------------------
    // NEGATIVE FIXTURES (§20)
    // -------------------------------------------------------------------

    val NEGATIVE_CONFIG_NOT_RUNNABLE = GoldenFixture(
        fixtureId = "negative-config-not-runnable",
        description = "Documentation-only config (UNKNOWN dims) must fail",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = PreprocessingConfig.pendingUnverified("golden-negative"),
        category = FixtureCategory.NEGATIVE,
        tags = listOf("negative", "config"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            expectedFailureCode = com.example.feedsense.analysis.ml.preprocess.PreprocessFailureCode.CONFIG_NOT_RUNNABLE,
            description = "pendingUnverified config -> CONFIG_NOT_RUNNABLE"
        )
    )

    // -------------------------------------------------------------------
    // PERFORMANCE SANITY FIXTURE (§25)
    // -------------------------------------------------------------------

    val PERF_SANITY_SMALL = GoldenFixture(
        fixtureId = "perf-sanity-small",
        description = "2x2 frame: verify preprocessing completes in bounded time",
        sourcePattern = SourcePattern.ALL_BLACK,
        sourceWidth = 2,
        sourceHeight = 2,
        configOverride = SCALE_ONLY_CONFIG,
        category = FixtureCategory.PERFORMANCE_SANITY,
        tags = listOf("performance", "sanity"),
        expectation = GoldenExpectation(
            expectedWidth = 2,
            expectedHeight = 2,
            expectedChannels = 3,
            expectedTensorType = "FLOAT32",
            description = "Performance sanity: bounded allocation"
        )
    )

    /**
     * The complete initial fixture corpus.
     */
    val ALL_FIXTURES: List<GoldenFixture> = listOf(
        // Basic images
        ALL_BLACK_4x4, ALL_WHITE_4x4, PORTRAIT_6x8, LANDSCAPE_8x6, SQUARE_4x4,
        // Orientation
        ORIENTATION_0, ORIENTATION_90, ORIENTATION_180, ORIENTATION_270,
        // Dimensions
        DIM_1x1, DIM_2x2_VERY_SMALL, DIM_ODD_5x7, DIM_LARGER_THAN_TARGET, DIM_SMALLER_THAN_TARGET,
        // Pixel patterns
        PATTERN_CHANNEL_ISOLATION, PATTERN_HIGH_CONTRAST, PATTERN_GRADIENT, PATTERN_GRAYSCALE_LIKE,
        // Channel order
        CHANNEL_RGB, CHANNEL_BGR,
        // Alpha
        ALPHA_DISCARD_OPAQUE, ALPHA_DISCARD_TRANSLUCENT, ALPHA_COMPOSITE, ALPHA_PRESERVE_RGBA,
        // Resize/crop/padding
        RESIZE_PORTRAIT_TO_SQUARE, RESIZE_LANDSCAPE_TO_SQUARE,
        PAD_WIDE_TO_SQUARE, PAD_TALL_TO_SQUARE, RESIZE_BILINEAR,
        // Normalization
        NORM_SCALE_ONLY_BLACK, NORM_SCALE_ONLY_WHITE,
        NORM_STD_BLACK, NORM_STD_WHITE, NORM_RED_DOMINANT,
        // Datatype/layout
        DATATYPE_FLOAT32, DATATYPE_INT8, DATATYPE_UINT8,
        // Privacy
        PRIVACY_ACCEPTED_SANITIZED, PRIVACY_ACCEPTED_NOT_REQUIRED,
        PRIVACY_REJECTED_FAILED, PRIVACY_REJECTED_UNAVAILABLE, PRIVACY_REJECTED_UNKNOWN,
        // Negative
        NEGATIVE_CONFIG_NOT_RUNNABLE,
        // Performance
        PERF_SANITY_SMALL
    )

    /**
     * Fixtures by category for selective test execution.
     */
    fun byCategory(category: FixtureCategory): List<GoldenFixture> =
        ALL_FIXTURES.filter { it.category == category }

    /**
     * Fixtures by tag.
     */
    fun byTag(tag: String): List<GoldenFixture> =
        ALL_FIXTURES.filter { tag in it.tags }
}
