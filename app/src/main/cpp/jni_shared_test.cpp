#include <jni.h>


#include <istream>
#include <cstdio>
#include <iostream>
#include <fstream>
#include <pthread.h>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include "xmp.h"

#include <android/log.h>

#define LOG_TAG "JNI-libxmp"

#define LOG_E(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,  __VA_ARGS__);
#define LOG_D(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG,  __VA_ARGS__);

static pthread_mutex_t lock_context;
static pthread_mutex_t lock_frameinfo;


static int16_t *buffer[2]; // buffer for audio
static size_t buffer_size = 192; // actual buffer size 44100 counts in 16bit
static uint8_t currentbuffer = 0;
static size_t renderedSz[2];
static size_t sample_rate = 48000;

static SLObjectItf engineObject;
static SLEngineItf engineEngine;
static SLObjectItf outputMixObject;

static SLObjectItf  bqPlayerObject = NULL;
static SLPlayItf    bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf    bqPlayerBufferQueue;
static SLVolumeItf                      bqPlayerVolume;

static bool isPaused = true;
static bool isLoaded = false;

static xmp_context ctx = NULL;
static xmp_module_info mi;
static xmp_frame_info fi;

extern "C" {

static void playerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    //LOG_D("Called! playerCallback()");
    SLresult res;
    if(isPaused) return;
    if(!isLoaded) return;

    pthread_mutex_lock(&lock_context);
    pthread_mutex_lock(&lock_frameinfo);
    // TODO: Write Callback function.
    /*
    xmp_play_buffer(ctx,buffer[currentbuffer],buffer_size,0);
    renderedSz[currentbuffer] = buffer_size;
    xmp_get_frame_info(ctx,&fi);
    */
    xmp_play_frame(ctx);
    xmp_get_frame_info(ctx,&fi);
    pthread_mutex_unlock(&lock_context);

    //res = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, buffer[currentbuffer],renderedSz[currentbuffer] * sizeof(int16_t) * 2); // in byte.
    res = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue,fi.buffer,fi.buffer_size);
    pthread_mutex_unlock(&lock_frameinfo);

    if(res != SL_RESULT_SUCCESS)
        LOG_D("Error on Enqueue? %d", res);
    //currentbuffer ^= 1; // first, render.
    // this requires buffer count in sample. render will be *2 internally
}

static void togglePause() {
    isPaused = !isPaused;
    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, buffer[0],sizeof(buffer[0]));
    currentbuffer ^= 1;
}

static void stopPlaying() {
    isPaused = true;
    memset(buffer[0],0,buffer_size * sizeof(int16_t) * 2);
    //memset(buffer[1],0,buffer_size * sizeof(int16_t) * 2);
    isLoaded = false;
    if(ctx != NULL) xmp_free_context(ctx);
    // TODO: Write Load function

    isPaused = false;
}


};

void startOpenSLES(int nsr, int fpb) {
    SLresult res;
    SLDataLocator_OutputMix loc_outMix;
    SLDataSink audioSnk;

    assert(pthread_mutex_init(&lock_context, NULL) == 0);
    assert(pthread_mutex_init(&lock_frameinfo, NULL) == 0);

    //Init
    ctx = xmp_create_context();

    buffer_size = fpb; // Stereo
    sample_rate = nsr;

    LOG_D("C-Side: OpenSL start with sample rate %d, buffersz %d",sample_rate,buffer_size);

    // allocate buffer
    buffer[0] = static_cast<int16_t *>(malloc(buffer_size * sizeof(int16_t) * 2)); // buffer_size is in 16bit. malloc() returns in byte. and render in stereo.
    //buffer[1] = static_cast<int16_t *>(malloc(buffer_size * sizeof(int16_t) * 2));

    memset(buffer[0],0,buffer_size * sizeof(int16_t) * 2);
    //memset(buffer[1],0,buffer_size * sizeof(int16_t) * 2);

    res = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    assert(res == SL_RESULT_SUCCESS);
    res = (*engineObject)->Realize(engineObject,SL_BOOLEAN_FALSE);
    assert(res == SL_RESULT_SUCCESS);
    res = (*engineObject)->GetInterface(engineObject,SL_IID_ENGINE,&engineEngine);
    assert(res == SL_RESULT_SUCCESS);
    res = (*engineEngine)->CreateOutputMix(engineEngine,&outputMixObject,0,NULL,NULL);
    assert(res == SL_RESULT_SUCCESS);
    res = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    assert(res == SL_RESULT_SUCCESS);

    //SLDataFormat_PCM format_pcm;
    SLDataLocator_AndroidSimpleBufferQueue locBufQ = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 4};
    SLAndroidDataFormat_PCM_EX format_pcm_ex = {
            .formatType = SL_ANDROID_DATAFORMAT_PCM_EX,
            .numChannels = 2,
            //.sampleRate = SL_SAMPLINGRATE_44_1,
            .sampleRate = nsr==48000?SL_SAMPLINGRATE_48:SL_SAMPLINGRATE_44_1,
            .bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_16,
            .containerSize = SL_PCMSAMPLEFORMAT_FIXED_16,
            //.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_32,
            //.containerSize = SL_PCMSAMPLEFORMAT_FIXED_32,
            .channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
            .endianness = SL_BYTEORDER_LITTLEENDIAN,
            //.representation = SL_ANDROID_PCM_REPRESENTATION_FLOAT
            .representation = SL_ANDROID_PCM_REPRESENTATION_SIGNED_INT
    };
    SLDataSource audioSrc = {
            .pLocator = &locBufQ,
            .pFormat = &format_pcm_ex
    };

    loc_outMix.locatorType = SL_DATALOCATOR_OUTPUTMIX;
    loc_outMix.outputMix = outputMixObject;
    audioSnk.pLocator = &loc_outMix;
    audioSnk.pFormat = NULL;

    const SLInterfaceID ids[2] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME};
    const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

    res = (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSnk, 2, ids, req);
    assert(res == SL_RESULT_SUCCESS);
    res = (*bqPlayerObject)->Realize(bqPlayerObject,SL_BOOLEAN_FALSE);
    assert(res == SL_RESULT_SUCCESS);
    res = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
    assert(res == SL_RESULT_SUCCESS);
    res = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,&bqPlayerBufferQueue);
    assert(res == SL_RESULT_SUCCESS);

    // This is where you set callback.
    res = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, playerCallback, NULL);
    assert(res == SL_RESULT_SUCCESS);
    res = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
    assert(res == SL_RESULT_SUCCESS);
    res = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED);
    assert(res == SL_RESULT_SUCCESS);

    res = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, buffer[currentbuffer],sizeof(buffer[currentbuffer]));

    if(res != SL_RESULT_SUCCESS) {
        abort();
    }

    currentbuffer ^= 1;
}

void endOpenSLES() {
    SLresult res;
    res = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
    assert(res == SL_RESULT_SUCCESS);

    if (bqPlayerObject != NULL)
    {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = NULL;
        bqPlayerPlay = NULL;
        bqPlayerBufferQueue = NULL;
        bqPlayerVolume = NULL;
    }

    if (outputMixObject != NULL)
    {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
    }

    if (engineObject != NULL)
    {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineEngine = NULL;
    }
    free(buffer[0]);
    free(buffer[1]);
}


extern "C"
JNIEXPORT void JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_helloworld(JNIEnv *env, jclass clazz) {
    LOG_D("Hello World!");
}
extern "C"
JNIEXPORT jstring JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getXMPVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(xmp_version);
}
extern "C"
JNIEXPORT void JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_startOpenSLES(JNIEnv *env, jclass clazz, jint nsr, jint fpb) {
    LOG_D("OpenSL start with sample rate %d, buffersz %d",nsr,fpb);

    startOpenSLES(nsr,fpb);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_loadFile(JNIEnv *env, jclass clazz, jstring filename) {
    if(ctx == NULL) ctx = xmp_create_context(); // Should've been done.
    (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED);

    const char* filepath = env->GetStringUTFChars(filename,0);

    FILE *fp = fopen(filepath,"rb");
    fseek(fp, 0, SEEK_END);
    size_t filesize = ftell(fp);
    LOG_D("Filesize %d", filesize);
    rewind(fp);
    // TODO: Write some configuration (You need to trash the load_module to change the config on-the-fly)
    int ret = xmp_load_module_from_file(ctx,fp,filesize);
    xmp_set_player(ctx, XMP_PLAYER_INTERP, XMP_INTERP_LINEAR);
    xmp_set_player(ctx, XMP_PLAYER_VOICES, 256);
    xmp_get_module_info(ctx,&mi);
    if(ret) {
        LOG_D("err: %d", ret);
        return false;
    }
    LOG_D("LOADED OK");
    isLoaded = true;
    isPaused = true;
    ret = xmp_start_player(ctx,sample_rate,0);
    LOG_D("xmp_Start_player() = %d",ret);

    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, buffer[currentbuffer],sizeof(buffer[currentbuffer]));
    currentbuffer ^= 1;
    fclose(fp);
    return true;
}


extern "C"
JNIEXPORT jstring JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getFrameInfo(JNIEnv *env, jclass clazz) {
    static char string_buf[256];
    pthread_mutex_lock(&lock_frameinfo);
    snprintf(string_buf,256,"Ptn %d Spd %d Bpm %d Row %d",fi.pattern,fi.speed,fi.bpm,fi.row);
    pthread_mutex_unlock(&lock_frameinfo);
    return env->NewStringUTF(string_buf);
}
extern "C"
JNIEXPORT void JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_togglePause(JNIEnv *env, jclass clazz) {
    togglePause();
    (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
}
extern "C"
JNIEXPORT jstring JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getChannelInfo(JNIEnv *env, jclass clazz, jint ch) {
    static char string_buf[256];
    if(ch >= 64) ch = 63;
    pthread_mutex_lock(&lock_frameinfo);
    xmp_channel_info ci = fi.channel_info[ch];
    pthread_mutex_unlock(&lock_frameinfo);
    snprintf(string_buf,256,"%02d: Per %08X Pos %08X",ch,ci.period,ci.position);
    return env->NewStringUTF(string_buf);
}
extern "C"
JNIEXPORT jint JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getChannels(JNIEnv *env, jclass clazz) {
    return mi.mod->chn;
}