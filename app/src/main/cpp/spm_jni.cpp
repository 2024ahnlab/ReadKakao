#include <jni.h>
#include "sentencepiece_processor.h"

static sentencepiece::SentencePieceProcessor processor;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_sentencepiece_SentencePieceProcessor_load(JNIEnv* env, jobject thiz, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    auto status = processor.Load(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return status.ok() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_org_sentencepiece_SentencePieceProcessor_encodeAsIds(JNIEnv* env, jobject thiz, jstring text) {
    const char* ctext = env->GetStringUTFChars(text, nullptr);
    std::vector<int> ids;
    processor.Encode(ctext, &ids);
    env->ReleaseStringUTFChars(text, ctext);

    jintArray result = env->NewIntArray(ids.size());
    env->SetIntArrayRegion(result, 0, ids.size(), ids.data());
    return result;
}

}
