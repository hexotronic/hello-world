// JNI bridge between Kotlin (LlamaNative) and llama.cpp.
// One handle = one loaded model with one context; calls on a handle must not overlap.

#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "LauscherLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#define LOGW(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#endif

namespace {

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    int n_ctx = 0;
    int n_batch = 0;
    std::atomic<bool> cancel{false};
};

void log_callback(ggml_log_level level, const char * text, void *) {
    // Only real problems; loading progress (dots) and info lines would flood logcat.
    if (level == GGML_LOG_LEVEL_WARN || level == GGML_LOG_LEVEL_ERROR) {
        LOGW("%s", text);
    }
}

std::string to_string(JNIEnv * env, jstring s) {
    if (s == nullptr) return {};
    const char * chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

// Kotlin strings may contain characters outside the BMP, which GetStringUTFChars encodes
// as "modified UTF-8"; passing raw UTF-8 bytes avoids that.
std::string bytes_to_string(JNIEnv * env, jbyteArray bytes) {
    if (bytes == nullptr) return {};
    const jsize n = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(n), '\0');
    env->GetByteArrayRegion(bytes, 0, n, reinterpret_cast<jbyte *>(&out[0]));
    return out;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & text) {
    const int n = -llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), nullptr, 0, true, true);
    std::vector<llama_token> tokens(static_cast<size_t>(n > 0 ? n : 0));
    if (n > 0) {
        llama_tokenize(vocab, text.data(), static_cast<int32_t>(text.size()), tokens.data(), n, true, true);
    }
    return tokens;
}

std::string piece(const llama_vocab * vocab, llama_token token) {
    char buf[256];
    const int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
    if (n < 0) {
        std::string big(static_cast<size_t>(-n), '\0');
        llama_token_to_piece(vocab, token, &big[0], -n, 0, false);
        return big;
    }
    return std::string(buf, static_cast<size_t>(n));
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_hexotronic_lauscher_llm_LlamaNative_nativeLoad(
        JNIEnv * env, jclass, jstring path, jint n_ctx, jint n_threads) {
    static bool backend_ready = false;
    if (!backend_ready) {
        llama_log_set(log_callback, nullptr);
        llama_backend_init();
        backend_ready = true;
    }
    const std::string model_path = to_string(env, path);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(model_path.c_str(), mparams);
    if (model == nullptr) {
        LOGW("could not load model %s", model_path.c_str());
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    cparams.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        LOGW("could not create context");
        llama_model_free(model);
        return 0;
    }

    auto * s = new Session();
    s->model = model;
    s->ctx = ctx;
    s->vocab = llama_model_get_vocab(model);
    s->n_ctx = static_cast<int>(llama_n_ctx(ctx));
    s->n_batch = 512;
    LOGI("model loaded, n_ctx=%d", s->n_ctx);
    return reinterpret_cast<jlong>(s);
}

/** Formats system + user message with the model's own chat template. */
JNIEXPORT jbyteArray JNICALL
Java_io_github_hexotronic_lauscher_llm_LlamaNative_nativeChatPrompt(
        JNIEnv * env, jclass, jlong handle, jbyteArray system, jbyteArray user) {
    auto * s = reinterpret_cast<Session *>(handle);
    const std::string sys = bytes_to_string(env, system);
    const std::string usr = bytes_to_string(env, user);
    llama_chat_message messages[2] = {
        {"system", sys.c_str()},
        {"user", usr.c_str()},
    };
    const char * tmpl = llama_model_chat_template(s->model, nullptr);
    std::vector<char> buf(sys.size() + usr.size() + 1024);
    int n = llama_chat_apply_template(tmpl, messages, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    if (n > static_cast<int>(buf.size())) {
        buf.resize(static_cast<size_t>(n));
        n = llama_chat_apply_template(tmpl, messages, 2, true, buf.data(), static_cast<int32_t>(buf.size()));
    }
    if (n < 0) {
        // Unknown template: fall back to ChatML, which most small instruct models understand.
        const std::string chatml = "<|im_start|>system\n" + sys + "<|im_end|>\n<|im_start|>user\n" + usr +
                                   "<|im_end|>\n<|im_start|>assistant\n";
        return to_bytes(env, chatml);
    }
    return to_bytes(env, std::string(buf.data(), static_cast<size_t>(n)));
}

JNIEXPORT jint JNICALL
Java_io_github_hexotronic_lauscher_llm_LlamaNative_nativeCountTokens(
        JNIEnv * env, jclass, jlong handle, jbyteArray text) {
    auto * s = reinterpret_cast<Session *>(handle);
    return static_cast<jint>(tokenize(s->vocab, bytes_to_string(env, text)).size());
}

JNIEXPORT jint JNICALL
Java_io_github_hexotronic_lauscher_llm_LlamaNative_nativeContextSize(JNIEnv *, jclass, jlong handle) {
    return reinterpret_cast<Session *>(handle)->n_ctx;
}

/**
 * Generates a completion. With a GBNF [grammar] the output is guaranteed to match it
 * (used to force valid JSON). Returns UTF-8 bytes; an empty result signals an error.
 */
JNIEXPORT jbyteArray JNICALL
Java_io_github_hexotronic_lauscher_llm_LlamaNative_nativeGenerate(
        JNIEnv * env, jclass, jlong handle, jbyteArray prompt_bytes, jstring grammar_str,
        jint max_tokens, jfloat temperature, jint seed) {
    auto * s = reinterpret_cast<Session *>(handle);
    s->cancel.store(false);
    const std::string prompt = bytes_to_string(env, prompt_bytes);
    const std::string grammar = to_string(env, grammar_str);

    llama_memory_clear(llama_get_memory(s->ctx), true);

    std::vector<llama_token> tokens = tokenize(s->vocab, prompt);
    if (tokens.empty() || static_cast<int>(tokens.size()) + max_tokens > s->n_ctx) {
        LOGW("prompt too long: %zu tokens, context %d", tokens.size(), s->n_ctx);
        return to_bytes(env, "");
    }

    llama_sampler * chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!grammar.empty()) {
        llama_sampler * g = llama_sampler_init_grammar(s->vocab, grammar.c_str(), "root");
        if (g == nullptr) {
            LOGW("invalid grammar");
            llama_sampler_free(chain);
            return to_bytes(env, "");
        }
        llama_sampler_chain_add(chain, g);
    }
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(chain, llama_sampler_init_min_p(0.05f, 1));
        llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(chain, llama_sampler_init_dist(static_cast<uint32_t>(seed)));
    }

    // Feed the prompt in batches.
    for (size_t i = 0; i < tokens.size(); i += static_cast<size_t>(s->n_batch)) {
        const int n = static_cast<int>(std::min(tokens.size() - i, static_cast<size_t>(s->n_batch)));
        llama_batch batch = llama_batch_get_one(tokens.data() + i, n);
        if (llama_decode(s->ctx, batch) != 0) {
            LOGW("decode failed while reading the prompt");
            llama_sampler_free(chain);
            return to_bytes(env, "");
        }
        if (s->cancel.load()) {
            llama_sampler_free(chain);
            return to_bytes(env, "");
        }
    }

    std::string out;
    for (int i = 0; i < max_tokens; ++i) {
        if (s->cancel.load()) break;
        llama_token token = llama_sampler_sample(chain, s->ctx, -1);
        if (llama_vocab_is_eog(s->vocab, token)) break;
        out += piece(s->vocab, token);
        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(s->ctx, batch) != 0) {
            LOGW("decode failed while generating");
            break;
        }
    }
    llama_sampler_free(chain);
    return to_bytes(env, out);
}

JNIEXPORT void JNICALL
Java_io_github_hexotronic_lauscher_llm_LlamaNative_nativeCancel(JNIEnv *, jclass, jlong handle) {
    if (handle != 0) reinterpret_cast<Session *>(handle)->cancel.store(true);
}

JNIEXPORT void JNICALL
Java_io_github_hexotronic_lauscher_llm_LlamaNative_nativeFree(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    auto * s = reinterpret_cast<Session *>(handle);
    llama_free(s->ctx);
    llama_model_free(s->model);
    delete s;
}

} // extern "C"
