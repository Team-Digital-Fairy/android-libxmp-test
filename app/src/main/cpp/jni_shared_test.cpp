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

static const char *note_name[] = {"C-", "C#", "D-", "D#", "E-", "F-",
                                  "F#", "G-", "G#", "A-", "A#", "B-"};

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
    // TODO: Can we do it better?
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
    buffer[1] = static_cast<int16_t *>(malloc(buffer_size * sizeof(int16_t) * 2));

    memset(buffer[0],0,buffer_size * sizeof(int16_t) * 2);
    memset(buffer[1],0,buffer_size * sizeof(int16_t) * 2);

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
    SLDataLocator_AndroidSimpleBufferQueue locBufQ = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 8};
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
    //free(buffer[1]);
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
    isPaused = true;
    isLoaded = false;


    struct xmp_test_info ti;
    int ret;
    const char* filepath = env->GetStringUTFChars(filename,0);

    xmp_end_player(ctx);
    if(isLoaded) xmp_release_module(ctx);
    FILE *fp = fopen(filepath,"rb");
    if(fp == NULL) {
        LOG_E("File is not a valid file");
        return false;
    }
    if((ret = xmp_test_module_from_file(fp,&ti))) {
        LOG_E("test failed: err=%d",ret);
        return false;
    }
    LOG_D("Check OK: %s %s",ti.name,ti.type);

    // get file size
    fseek(fp, 0, SEEK_END);
    size_t filesize = ftell(fp);
    LOG_D("Filesize %u", filesize);
    rewind(fp);
    // get filesize end

    // TODO: Write some configuration (You need to trash the load_module to change the config on-the-fly)
    ret = xmp_load_module_from_file(ctx,fp,filesize);
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
    /*
    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, buffer[currentbuffer],sizeof(buffer[currentbuffer]));
    currentbuffer ^= 1;
     */
    // Kick start

    fclose(fp);
    return true;
}


extern "C"
JNIEXPORT jstring JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getFrameInfo(JNIEnv *env, jclass clazz) {
    static char string_buf[256];
    pthread_mutex_lock(&lock_frameinfo);
    xmp_frame_info cur_fi = fi;
    pthread_mutex_unlock(&lock_frameinfo);

    snprintf(string_buf,256,"Ptn %02X Spd %d Bpm %3d Row %2d (V)RT %d/%d",cur_fi.pattern,cur_fi.speed,cur_fi.bpm,cur_fi.row,cur_fi.time,cur_fi.total_time);
    return env->NewStringUTF(string_buf);
}

extern "C"
JNIEXPORT void JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_togglePause(JNIEnv *env, jclass clazz) {
    isPaused = !isPaused;
    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, buffer[0],8);

    currentbuffer ^= 1;

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
extern "C"
JNIEXPORT jlong JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getRunningTime(JNIEnv *env, jclass clazz) {
    return fi.time;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getTotalTime(JNIEnv *env, jclass clazz) {
    return fi.total_time;
}


extern "C"
JNIEXPORT jstring JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getRowEvt(JNIEnv *env, jclass clazz, jint row, jint channel) {
    // TODO: This function is very heavy to use if you call often.
    static char string_buf[128];
    static char nb[8];
    pthread_mutex_lock(&lock_frameinfo);
    struct xmp_channel_info *current_ci = &fi.channel_info[channel];
    pthread_mutex_unlock(&lock_frameinfo);
    struct xmp_event *evt = &current_ci->event;



    if(evt->note > 0x80)
        snprintf(nb,5,"===");
    else if(evt->note > 0)
        snprintf(nb,5,"%s%d",note_name[evt->note % 12],evt->note / 12);
    else
        snprintf(nb,5,"---");

    snprintf(string_buf,128,"%s %02X %d-%d",nb,evt->ins,evt->fxt,evt->fxp);

    return env->NewStringUTF(string_buf);

}
extern "C"
JNIEXPORT jint JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getCurrentRow(JNIEnv *env, jclass clazz) {
    pthread_mutex_lock(&lock_frameinfo);
    int r = fi.row;
    pthread_mutex_unlock(&lock_frameinfo);
    return r;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getRowString(JNIEnv *env, jclass clazz, jint pattern, jint size) {
    // Returns string of each line
    // Like
    // C-500 C-603... return everything from row

    static char string_buf[8192];
    static char nb[8];
    static char vol[8];
    pthread_mutex_lock(&lock_frameinfo);
    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(fi.num_rows,string_class, nullptr);
    pthread_mutex_unlock(&lock_frameinfo);
    int rowlen = mi.mod->xxp[pattern]->rows;
    int channels = mi.mod->chn;
    //LOG_D("Query P %d got ROWLEN = %d",pattern,rowlen)

    for(int i=0;i<rowlen; i++) {
        // get all track, and eval each event in one row
        int buf_pos = 4;
        for(int t=0; t<channels; t++) {
            int trk_indx = mi.mod->xxp[pattern]->index[t];
            struct xmp_event *evt = &mi.mod->xxt[trk_indx]->event[i];
            int evte = evt->note;
            int ins = evt->ins;

            if(evte > 0x80)
                snprintf(nb,4,"===");
            else if(evte > 0) {
                int n = evte - 1;
                snprintf(nb,4,"%s%1d",note_name[n % 12], n/12);
            } else snprintf(nb,4,"---");

            if(ins > 0) snprintf(nb+5,3,"%02X",ins);
            else snprintf(nb+5,3,"--");

            // Volume
            if(evt->vol == 0 && evt->f2t != 0) {
                // Volume Column is event
                snprintf(vol,4,"%01d%02X",evt->f2t,evt->f2p);

            } else if (evt->vol != 0) {
                // Volume is actually volume
                snprintf(vol,4,"v%02X",evt->vol);

            } else {
                // None
                snprintf(vol,4,"---");
            }

            switch(size) {
                case 0:
                default:
                    // Note+Ins (Low)
                    buf_pos += snprintf(string_buf + buf_pos, 8192 - buf_pos, "%s%s ", nb, nb + 5);
                    break;

                case 1:

                    // Parse volume column; Includes
                    // Note+Ins+Vol (Mid)
                    buf_pos += snprintf(string_buf + buf_pos, 8192 - buf_pos, "%s%s%s ", nb, nb + 5, vol);
                    break;

                case 2:
                    // Note+Ins+Vol+Eff (Hi)
                    buf_pos += snprintf(string_buf + buf_pos, 8192 - buf_pos, "%s%s%s--- ", nb, nb + 5, vol);
                    break;


            }
        }
        snprintf(nb,5,"%02X: ",i);
        memcpy(string_buf,nb,4);
        //LOG_D("%s",string_buf)
        env->SetObjectArrayElement(arr,i,env->NewStringUTF(string_buf));
    }


    return arr;
}
extern "C"
JNIEXPORT jint JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getCurrentPattern(JNIEnv *env, jclass clazz) {
    pthread_mutex_lock(&lock_frameinfo);
    xmp_frame_info cur_fi = fi;
    pthread_mutex_unlock(&lock_frameinfo);
    return cur_fi.pattern;
}
extern "C"
JNIEXPORT jint JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getTotalRows(JNIEnv *env, jclass clazz) {
    pthread_mutex_lock(&lock_frameinfo);
    xmp_frame_info cur_fi = fi;
    pthread_mutex_unlock(&lock_frameinfo);
    return cur_fi.num_rows;
}
extern "C"
JNIEXPORT jint JNICALL
Java_team_digitalfairy_lencel_jni_1shared_1test_LibXMP_getOrdinal(JNIEnv *env, jclass clazz) {
    pthread_mutex_lock(&lock_frameinfo);
    xmp_frame_info cur_fi = fi;
    pthread_mutex_unlock(&lock_frameinfo);
    return cur_fi.pos;
}