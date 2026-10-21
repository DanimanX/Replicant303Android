#include <jni.h>

#include <algorithm>
#include <memory>
#include <string>

#include "engine/audio_driver.h"
#include "engine/engine.h"

namespace {

struct NativeContext {
    std::unique_ptr<r303::Engine> engine;
    std::unique_ptr<r303::AudioDriver> driver;
};

NativeContext* context(jlong handle) {
    return reinterpret_cast<NativeContext*>(handle);
}

r303::Engine* engine(jlong handle) {
    NativeContext* ctx = context(handle);
    return ctx != nullptr ? ctx->engine.get() : nullptr;
}

std::string toStdString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring toJString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeCreate(JNIEnv*, jobject) {
    auto ctx = std::make_unique<NativeContext>();
    ctx->engine = std::make_unique<r303::Engine>();
    ctx->engine->prepare(48000.0, 512);
    ctx->driver = std::make_unique<r303::AudioDriver>(ctx->engine.get());
    return reinterpret_cast<jlong>(ctx.release());
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete context(handle);
}

JNIEXPORT jstring JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("1.0.0");
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeStartAudio(JNIEnv*, jobject, jlong handle) {
    NativeContext* ctx = context(handle);
    if (ctx == nullptr || !ctx->driver) return JNI_FALSE;
    return ctx->driver->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeStopAudio(JNIEnv*, jobject, jlong handle) {
    NativeContext* ctx = context(handle);
    if (ctx != nullptr && ctx->driver) ctx->driver->stop();
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeIsAudioRunning(JNIEnv*, jobject,
                                                                     jlong handle) {
    NativeContext* ctx = context(handle);
    if (ctx == nullptr || !ctx->driver) return JNI_FALSE;
    return ctx->driver->isRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeRenderSilent(JNIEnv*, jobject,
                                                                    jlong handle, jint frames) {
    NativeContext* ctx = context(handle);
    if (ctx == nullptr) return;
    if (ctx->driver) ctx->driver->renderSilentIfIdle(frames);
    else if (ctx->engine) ctx->engine->renderSilent(frames);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeGetAudioStats(JNIEnv* env, jobject,
                                                                     jlong handle,
                                                                     jfloatArray floats,
                                                                     jintArray ints) {
    NativeContext* ctx = context(handle);
    if (ctx == nullptr || !ctx->driver) return;
    const auto stats = ctx->driver->stats();
    if (floats != nullptr && env->GetArrayLength(floats) >= 2) {
        const jfloat values[2] = {static_cast<jfloat>(stats.latencyMs),
                                  static_cast<jfloat>(stats.cpuLoad)};
        env->SetFloatArrayRegion(floats, 0, 2, values);
    }
    if (ints != nullptr && env->GetArrayLength(ints) >= 6) {
        const jint values[6] = {stats.xRuns, stats.sampleRate, stats.framesPerBurst,
                                stats.bufferFrames, stats.api, stats.sharing};
        env->SetIntArrayRegion(ints, 0, 6, values);
    }
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeGetParams(JNIEnv* env, jobject,
                                                                 jlong handle, jfloatArray out) {
    r303::Engine* e = engine(handle);
    if (e == nullptr || out == nullptr) return;
    const jsize length = env->GetArrayLength(out);
    if (length < r303::kParamCount) return;
    jfloat values[r303::kParamCount] = {};
    e->getParams(values, r303::kParamCount);
    env->SetFloatArrayRegion(out, 0, r303::kParamCount, values);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeGetStatus(JNIEnv* env, jobject,
                                                                 jlong handle, jintArray out) {
    r303::Engine* e = engine(handle);
    if (e == nullptr || out == nullptr) return;
    const jsize length = env->GetArrayLength(out);
    if (length < r303::kStatusCount) return;
    jint values[r303::kStatusCount] = {};
    e->getStatus(values, r303::kStatusCount);
    env->SetIntArrayRegion(out, 0, r303::kStatusCount, values);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeGetScope(JNIEnv* env, jobject,
                                                                jlong handle, jfloatArray out) {
    r303::Engine* e = engine(handle);
    if (e == nullptr || out == nullptr) return;
    const jsize length = env->GetArrayLength(out);
    jfloat values[256] = {};
    const int count = std::min<int>(length, 256);
    e->getScope(values, count);
    env->SetFloatArrayRegion(out, 0, count, values);
}

JNIEXPORT jfloat JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeGetMeter(JNIEnv*, jobject, jlong handle) {
    r303::Engine* e = engine(handle);
    return e != nullptr ? e->meter() : 0.0f;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetParam(JNIEnv*, jobject, jlong handle,
                                                                jint id, jfloat value) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setParam(id, value);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetPlaying(JNIEnv*, jobject, jlong handle,
                                                                  jboolean playing) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setPlaying(playing == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeTogglePlaying(JNIEnv*, jobject,
                                                                     jlong handle) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->togglePlaying();
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeIsPlaying(JNIEnv*, jobject, jlong handle) {
    r303::Engine* e = engine(handle);
    return (e != nullptr && e->isPlaying()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetTempo(JNIEnv*, jobject, jlong handle,
                                                                jdouble bpm) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setTempo(static_cast<double>(bpm));
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetSwing(JNIEnv*, jobject, jlong handle,
                                                                jfloat value) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setSwing(value);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetPatternLength(JNIEnv*, jobject,
                                                                        jlong handle,
                                                                        jint length) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setPatternLength(length);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetCurrentPattern(JNIEnv*, jobject,
                                                                         jlong handle,
                                                                         jint pattern) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setCurrentPattern(pattern);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetStep(JNIEnv*, jobject, jlong handle,
                                                               jint patternIndex, jint stepIndex,
                                                               jboolean gate, jint note,
                                                               jboolean accent, jboolean slide) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) {
        e->setStep(patternIndex, stepIndex, gate == JNI_TRUE, note, accent == JNI_TRUE,
                   slide == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeClearPattern(JNIEnv*, jobject,
                                                                    jlong handle,
                                                                    jint patternIndex) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->clearPattern(patternIndex);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeInitPattern(JNIEnv*, jobject,
                                                                   jlong handle,
                                                                   jint patternIndex) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->initPattern(patternIndex);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeCopyPattern(JNIEnv*, jobject,
                                                                   jlong handle, jint from,
                                                                   jint to) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->copyPattern(from, to);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeGetPatternRow(JNIEnv* env, jobject,
                                                                     jlong handle,
                                                                     jint patternIndex,
                                                                     jintArray out) {
    r303::Engine* e = engine(handle);
    if (e == nullptr || out == nullptr) return;
    const jsize length = env->GetArrayLength(out);
    if (length < 16) return;
    jint values[16] = {};
    e->getPatternRow(patternIndex, values, 16);
    env->SetIntArrayRegion(out, 0, 16, values);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeNoteOn(JNIEnv*, jobject, jlong handle,
                                                              jint note, jint velocity) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->noteOn(note, velocity);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeNoteOff(JNIEnv*, jobject, jlong handle,
                                                               jint note) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->noteOff(note);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeAllNotesOff(JNIEnv*, jobject,
                                                                   jlong handle) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->allNotesOff();
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeLoadFactoryPreset(JNIEnv*, jobject,
                                                                         jlong handle,
                                                                         jint bank, jint slot) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->loadFactoryPreset(bank, slot);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeGeneratePreset(JNIEnv*, jobject,
                                                                      jlong handle, jint bank,
                                                                      jint slot, jint seed) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) {
        e->generatePreset(bank, slot, static_cast<uint32_t>(seed));
    }
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeGetPreset(JNIEnv* env, jobject,
                                                                 jlong handle, jintArray out) {
    r303::Engine* e = engine(handle);
    if (e == nullptr || out == nullptr) return;
    if (env->GetArrayLength(out) < r303::presetbank::kWireSize) return;
    const r303::Preset preset = e->capturePreset();
    jint values[r303::presetbank::kWireSize] = {};
    r303::presetbank::toWire(preset, values, r303::presetbank::kWireSize);
    env->SetIntArrayRegion(out, 0, r303::presetbank::kWireSize, values);
}

JNIEXPORT jboolean JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeApplyPreset(JNIEnv* env, jobject,
                                                                   jlong handle, jintArray values,
                                                                   jstring name) {
    r303::Engine* e = engine(handle);
    if (e == nullptr || values == nullptr) return JNI_FALSE;
    if (env->GetArrayLength(values) < r303::presetbank::kWireSize) return JNI_FALSE;

    jint raw[r303::presetbank::kWireSize] = {};
    env->GetIntArrayRegion(values, 0, r303::presetbank::kWireSize, raw);

    r303::Preset preset;
    if (!r303::presetbank::fromWire(raw, r303::presetbank::kWireSize, preset)) return JNI_FALSE;

    const std::string requested = toStdString(env, name);
    if (!requested.empty()) preset.name = requested;
    e->applyPreset(preset, e->currentPattern());
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeCurrentPresetName(JNIEnv* env, jobject,
                                                                         jlong handle) {
    r303::Engine* e = engine(handle);
    return toJString(env, e != nullptr ? e->currentPresetName() : std::string("Init"));
}

JNIEXPORT jstring JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeFactoryPresetName(JNIEnv* env, jobject,
                                                                         jlong, jint bank,
                                                                         jint slot) {
    return toJString(env, r303::Engine::factoryPresetName(bank, slot));
}

JNIEXPORT jint JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeGetPatternLength(JNIEnv*, jobject,
                                                                        jlong handle,
                                                                        jint patternIndex) {
    r303::Engine* e = engine(handle);
    return e != nullptr ? e->getPatternLength(patternIndex) : 16;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativePushMidi(JNIEnv*, jobject, jlong handle,
                                                                jint status, jint data1,
                                                                jint data2,
                                                                jlong timestampNanos) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->pushMidi(status, data1, data2, timestampNanos);
}

JNIEXPORT jint JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativePollMidiOut(JNIEnv* env, jobject,
                                                                   jlong handle, jbyteArray out) {
    r303::Engine* e = engine(handle);
    if (e == nullptr || out == nullptr) return 0;
    const jsize length = env->GetArrayLength(out);
    const int maxRecords = length / 16;
    if (maxRecords <= 0) return 0;

    jbyte buffer[16 * 128] = {};
    const int cap = std::min(maxRecords, 128);
    const int count = e->pollMidiOut(reinterpret_cast<uint8_t*>(buffer), cap);
    if (count > 0) env->SetByteArrayRegion(out, 0, count * 16, buffer);
    return count;
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetMidiOutEnabled(JNIEnv*, jobject,
                                                                         jlong handle,
                                                                         jboolean enabled) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setMidiOutEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetClockSend(JNIEnv*, jobject,
                                                                    jlong handle,
                                                                    jboolean enabled) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setClockSend(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetClockReceive(JNIEnv*, jobject,
                                                                       jlong handle,
                                                                       jboolean enabled) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setClockReceive(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetTransportSend(JNIEnv*, jobject,
                                                                        jlong handle,
                                                                        jboolean enabled) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setTransportSend(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_danimanx_replicant303_audio_NativeEngine_nativeSetMidiChannel(JNIEnv*, jobject,
                                                                      jlong handle,
                                                                      jint channel) {
    r303::Engine* e = engine(handle);
    if (e != nullptr) e->setMidiChannel(channel);
}

} // extern "C"
