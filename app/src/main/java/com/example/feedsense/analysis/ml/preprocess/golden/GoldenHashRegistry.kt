package com.example.feedsense.analysis.ml.preprocess.golden

// --------------------------------
// GOLDEN HASH REGISTRY (8B-15-4 §8)
// --------------------------------
//
// Pinned SHA-256 digests for the seed fixture corpus. These are the
// authoritative expected outputs of the 8B-15-3 preprocessing
// contract when run against the deterministic synthetic frames in
// [GoldenFixtureLoader] under [GoldenFixtureCorpus.CORPUS_VERSION].
//
// Pinning procedure (ONLY run when the preprocessing contract is
// deliberately changed):
//   1. Run GoldenHashHarnessTest (temporary) to print PINNED lines.
//   2. Copy the new values into this registry.
//   3. Delete the harness test and commit the reviewed diff.
//
// A mismatch between live output and a pinned hash FAILS the test
// suite; the hashes are never updated automatically.
//
// HASH_SCHEME: SHA-256 over
//   width(4B) | height(4B) | channels(4B) | tensorType(1B) | data
// where data is the little-endian IEEE-754 bit pattern of each float
// for FLOAT32 and the raw quantized bytes for INT8/UINT8.

/**
 * Pinned golden hashes keyed by [GoldenFixture.fixtureId].
 */
object GoldenHashRegistry {

    const val HASH_SCHEME = "SHA-256[width|height|channels|tensorType|data]"

    /**
     * Pinned tensor SHA-256 hashes (see [GoldenHasher.hashTensor]).
     */
    val PINNED_TENSOR_HASH: Map<String, String> = mapOf(
        "basic-all-black-4x4" to
            "2dc0d84f363d32d28c9420b3b79bac89650a476b1ae9b9aa75e508de87994989",
        "basic-all-white-4x4" to
            "50b3a0fca8c874ea8cd85178aef23de624291357265a1e9e382837a81c14a073",
        "orient-0-degrees" to
            "8c691fafc9dc02243b744c606f89928cb6e98a0c1fe81cfde73394dcff31ba2f",
        "orient-90-degrees" to
            "f9be6856e0cfd54dad2a47d7c9acd507719ab6ba6684fe8a01d407aac59ac9eb",
        "orient-180-degrees" to
            "0c954ab24e803b5df6b7bd12277e7b79d2967820e8db321494bfaf10842e1fdf",
        "orient-270-degrees" to
            "5c23290e1b949cd4699ce3f188ec1ce4492ec7081a2a1b413e0176a748d2f012",
        "pattern-rgb-channel-isolation" to
            "7c0b4df9b3dca88129f627335d03a53df708d2f3e350435c2dbb24969bb53ff8",
        "pattern-high-contrast-edges" to
            "2dc0d84f363d32d28c9420b3b79bac89650a476b1ae9b9aa75e508de87994989",
        "pattern-known-gradient" to
            "d21c974a58e56cb0bb211362c6a122c3a18bc566234871b1e4d45375e5333787",
        "pattern-grayscale-like" to
            "c4659d8b9279c1d6bbaa4f71a55d6e2ecca236df9401c5f01d526b7bc8296c13",
        "channel-rgb-order" to
            "2716d37d699dc87e3f2f068726a7fceec93d02718832914cc4694bdeb1d7e586",
        "channel-bgr-order" to
            "990b1bb48f21977a0e86d1695802458d8f7fd51ce3e20323c071ad659b72aa56",
        "norm-scale-only-black" to
            "2dc0d84f363d32d28c9420b3b79bac89650a476b1ae9b9aa75e508de87994989",
        "norm-scale-only-white" to
            "50b3a0fca8c874ea8cd85178aef23de624291357265a1e9e382837a81c14a073",
        "norm-std-black" to
            "281fec3b7bd25c0f348daa0a05c6e80cd59b4447aa7065cd1665062ad6289ef0",
        "norm-std-white" to
            "260e2f2c5091575038d8870fa36fb918a1e75acedfd612fd7963cafe72ef194e",
        "norm-red-dominant" to
            "c53e2de191da00e9067d848b406f74ed8c452bc445426e95fc378c13a7f896d9",
        "datatype-int8" to
            "03c050918213479037eb59cf11f1b9d8b4dde6f6912ca62313c9df666976d5b2",
        "datatype-uint8" to
            "5f220d29d67e9b587dda6097efb09afdd42d76ea4d5920d1f8037be05cf6eeba"
    )

    /**
     * Pinned preprocessed-input fingerprints keyed by fixture id
     * (see DeterministicPreprocessor output fingerprint).
     */
    val PINNED_FINGERPRINT: Map<String, String> = mapOf(
        "basic-all-black-4x4" to
            "de66678a79b33c457f578391251c4c23b031b7f9a4b324f42ef662e7e8297fee",
        "basic-all-white-4x4" to
            "de66678a79b33c457f578391251c4c23b031b7f9a4b324f42ef662e7e8297fee",
        "orient-0-degrees" to
            "9af09b598e56c7cb9d02868762be2a0f458e3dc3b3570acb0b1b6909a2c6e8ea",
        "orient-90-degrees" to
            "fa30b32d35e6c87d1a610d85455c7d624224ca795075714b228bf6938ed38939",
        "orient-180-degrees" to
            "84635586a1fc5a523d39cbf601603c7280ad6076bab265b95878c87a0eacd395",
        "orient-270-degrees" to
            "657d5a7f6bef3e9579ad339dd32d2b2e31c0452dea47c732461bfa91eb8c3bda",
        "pattern-rgb-channel-isolation" to
            "de66678a79b33c457f578391251c4c23b031b7f9a4b324f42ef662e7e8297fee",
        "pattern-high-contrast-edges" to
            "de66678a79b33c457f578391251c4c23b031b7f9a4b324f42ef662e7e8297fee",
        "pattern-known-gradient" to
            "de66678a79b33c457f578391251c4c23b031b7f9a4b324f42ef662e7e8297fee",
        "pattern-grayscale-like" to
            "de66678a79b33c457f578391251c4c23b031b7f9a4b324f42ef662e7e8297fee",
        "channel-rgb-order" to
            "1af31cede5448524106925857b26bcefe9d9153ed9262284dccf02b2a45d2ef6",
        "channel-bgr-order" to
            "f56d364b641ec697ad94f17bb10b367d6cd89f4d26f0048841ba0b2ec545d61f",
        "norm-scale-only-black" to
            "1af31cede5448524106925857b26bcefe9d9153ed9262284dccf02b2a45d2ef6",
        "norm-scale-only-white" to
            "1af31cede5448524106925857b26bcefe9d9153ed9262284dccf02b2a45d2ef6",
        "norm-std-black" to
            "087f8890e0e682d9bea93b9e951b85d1083bedd12d6e65f8a1ed3b8e6e87e28c",
        "norm-std-white" to
            "087f8890e0e682d9bea93b9e951b85d1083bedd12d6e65f8a1ed3b8e6e87e28c",
        "norm-red-dominant" to
            "087f8890e0e682d9bea93b9e951b85d1083bedd12d6e65f8a1ed3b8e6e87e28c",
        "datatype-int8" to
            "740fd7ea3347ad2e0ac9ebdeaf979867c358c4a0a88ff6f3dc068d902c6f1958",
        "datatype-uint8" to
            "1ab79aa6c944922d8f975bdbd0c487c273b20cc5ac7d0130e3d8dabe986ff1d9"
    )

    /**
     * Live updates to the registry are forbidden. The only way to
     * change a pinned hash is to edit the source and commit it.
     */
    fun pinnedTensorHash(fixtureId: String): String? =
        PINNED_TENSOR_HASH[fixtureId]

    fun pinnedFingerprint(fixtureId: String): String? =
        PINNED_FINGERPRINT[fixtureId]
}