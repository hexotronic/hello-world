package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.analysis.core.HfModels
import io.github.hexotronic.lauscher.analysis.core.RamFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HfModelsTest {
    @Test
    fun searchAddressesAndResults() {
        assertEquals(
            "https://huggingface.co/api/models?search=qwen%203b&filter=gguf&sort=downloads&direction=-1&limit=25",
            HfModels.searchUrl(" qwen 3b ", speech = false),
        )
        assertTrue("filter" !in HfModels.searchUrl("", speech = true))
        val json = """[
            {"_id":"1","id":"Qwen/Qwen2.5-1.5B-Instruct-GGUF","likes":120,"private":false,"downloads":250000,
             "tags":["gguf","chat","text-generation"],"pipeline_tag":"text-generation","modelId":"Qwen/Qwen2.5-1.5B-Instruct-GGUF"},
            {"_id":"2","id":"someone/secret","private":true,"downloads":5},
            {"_id":"3","id":"google/gemma-3-4b-it-qat-q4_0-gguf","likes":300,"downloads":90000,"gated":"manual","tags":["gguf"]}
        ]"""
        val repos = HfModels.parseSearch(json)
        assertEquals(listOf("Qwen/Qwen2.5-1.5B-Instruct-GGUF", "google/gemma-3-4b-it-qat-q4_0-gguf"), repos.map { it.id })
        assertFalse(repos[0].gated)
        assertTrue(repos[1].gated)
        assertEquals("Qwen", repos[0].author)
        assertTrue(HfModels.looksLikeChatModel(repos[0]) && HfModels.looksLikeChatModel(repos[1]))
        assertEquals(emptyList<Any>(), HfModels.parseSearch("Fehler"))
    }

    @Test
    fun filesAreFilteredAndTheRightOneIsSuggested() {
        val tree = """[
            {"type":"file","size":1519,"path":".gitattributes"},
            {"type":"file","size":134,"lfs":{"size":1930000000},"path":"Qwen2.5-3B-Instruct-Q8_0.gguf"},
            {"type":"file","size":134,"lfs":{"size":1100000000},"path":"Qwen2.5-3B-Instruct-Q2_K.gguf"},
            {"type":"file","size":134,"lfs":{"size":1930000000},"path":"Qwen2.5-3B-Instruct-Q4_K_M.gguf"},
            {"type":"file","size":134,"lfs":{"size":1800000000},"path":"Qwen2.5-3B-Instruct-IQ4_XS.gguf"},
            {"type":"file","size":134,"lfs":{"size":6000000000},"path":"Qwen2.5-3B-Instruct-F16.gguf"},
            {"type":"file","size":134,"lfs":{"size":800000000},"path":"mmproj-F16.gguf"},
            {"type":"file","size":134,"lfs":{"size":4000000000},"path":"big/Qwen2.5-3B-Instruct-Q4_K_M-00001-of-00002.gguf"},
            {"type":"directory","size":0,"path":"big"}
        ]"""
        val files = HfModels.parseFiles(tree, speech = false)
        assertEquals(
            listOf("Qwen2.5-3B-Instruct-Q4_K_M.gguf", "Qwen2.5-3B-Instruct-IQ4_XS.gguf", "Qwen2.5-3B-Instruct-Q8_0.gguf", "Qwen2.5-3B-Instruct-Q2_K.gguf", "Qwen2.5-3B-Instruct-F16.gguf"),
            files.map { it.path },
        )
        // 12 GB phone (Xiaomi 13T): Q4_K_M fits well; on a 4 GB phone only the smallest one fits.
        assertEquals("Qwen2.5-3B-Instruct-Q4_K_M.gguf", HfModels.recommended(files, 12_000)?.path)
        assertEquals(RamFit.GOOD, HfModels.fit(1_930_000_000, 12_000))
        assertEquals(RamFit.TOO_BIG, HfModels.fit(6_000_000_000, 8_000))
        assertEquals(RamFit.TIGHT, HfModels.fit(1_930_000_000, 6_000))
        assertEquals(RamFit.TOO_BIG, HfModels.fit(1_930_000_000, 4_000))
        assertEquals("Qwen2.5-3B-Instruct-Q2_K.gguf", HfModels.recommended(files, 4_000)?.path)

        val whisper = """[{"type":"file","size":1,"lfs":{"size":57000000},"path":"ggml-base-q5_1.bin"},
            {"type":"file","size":1,"lfs":{"size":148000000},"path":"ggml-base.bin"},
            {"type":"file","size":1,"lfs":{"size":38000000},"path":"ggml-base-encoder.mlmodelc.zip"},
            {"type":"file","size":1,"lfs":{"size":38000000},"path":"model.safetensors"}]"""
        assertEquals(listOf("ggml-base-q5_1.bin", "ggml-base.bin"), HfModels.parseFiles(whisper, speech = true).map { it.path })
    }

    @Test
    fun namesTellQuantisationAndSize() {
        assertEquals("Q4_K_M", HfModels.quantOf("qwen2.5-1.5b-instruct-q4_k_m.gguf"))
        assertEquals("IQ4_XS", HfModels.quantOf("Qwen3-4B-Instruct-2507-IQ4_XS.gguf"))
        assertEquals("Q4_K_XL", HfModels.quantOf("gemma-3-4b-it-UD-Q4_K_XL.gguf"))
        assertEquals("BF16", HfModels.quantOf("model-BF16.gguf"))
        assertEquals("Q5_1", HfModels.quantOf("ggml-small-q5_1.bin"))
        assertNull(HfModels.quantOf("qwen2.5.gguf"))
        assertEquals(1.5f, HfModels.billionsOf("Qwen/Qwen2.5-1.5B-Instruct-GGUF"))
        assertEquals(4f, HfModels.billionsOf("unsloth/Qwen3-4B-Instruct-2507-GGUF"))
        assertEquals(4f, HfModels.billionsOf("unsloth/gemma-3-4b-it-GGUF"))
        assertEquals(0.36f, HfModels.billionsOf("SmolLM2-360M-Instruct")!!, 0.001f)
        assertNull(HfModels.billionsOf("Phi-3.5-mini-instruct"))
        assertEquals(
            "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
            HfModels.downloadUrl("unsloth/Qwen3-4B-Instruct-2507-GGUF", "Qwen3-4B-Instruct-2507-Q4_K_M.gguf"),
        )
        assertEquals("hf-unsloth_Qwen3-4B-GGUF-Qwen3-4B-Q4_K_M.gguf", HfModels.localName("unsloth/Qwen3-4B-GGUF", "sub/Qwen3-4B-Q4_K_M.gguf"))
        assertEquals("1,2 Mio.", HfModels.count(1_234_567))
    }
}
