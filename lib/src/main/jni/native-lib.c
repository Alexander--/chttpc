#include <jni.h>
#include <android/log.h>
#include <curl/curl.h>
#include <dlfcn.h>
#include <alloca.h>
#include <curl/multi.h>
#include "hashmap.h"
#include <unistd.h>
#include <sys/syscall.h>
#include <bits/timespec.h>
#include <sys/select.h>
#include "linux_syscall_support.h"
#include "moar_syscall.h"

#undef stdin
#undef stdout
#undef stderr
FILE *stdin = &__sF[0];
FILE *stdout = &__sF[1];
FILE *stderr = &__sF[2];

#define PROP_VALUE_MAX  92

typedef int (*system_property_get)(const char *, char *);

typedef volatile _Atomic uint8_t* i10n_ptr;

enum OPTIONS {
    KEEPALIVE_PERIOD = 0,
    CONTINUE_TIMEOUT = 1,
    CONN_CACHE_SIZE = 2,
    MAX_REDIRECT_COUNT = 3,
};

#define FLAG_DEBUG 1u
#define FLAG_SEND_AFTER_ERROR (1u << 1)
#define FLAG_TCP_KEEP_ALIVE (1u << 2)
#define FLAG_TLS_FALSE_START (1u << 3)
#define FLAG_TCP_FAST_OPEN (1u << 4)
#define FLAG_RAW_RESPONSE (1u << 5)
#define FLAG_TCP_NODELAY (1u << 6)
#define FLAG_USE_IPV6 (1u << 7)
#define FLAG_REQUEST_COMPRESSION (1u << 8)

#define STATE_ATTACHED 1u
#define STATE_HANDLE_REDIRECT (1u << 1)
#define STATE_DO_INPUT (1u << 2)
#define STATE_RECV_PAUSED (1u << 3)
#define STATE_DO_OUTPUT (1u << 4)
#define STATE_SEND_PAUSED (1u << 5)
#define STATE_NEED_INPUT (1u << 6)
#define STATE_NEED_OUTPUT (1u << 7)
#define STATE_DONE_SENDING (1u << 8)
#define STATE_SEEN_HEADER_END (1u << 9)

#define SET_ATTACHED(i) (i->state |= STATE_ATTACHED)
#define SET_DETACHED(i) (i->state &= ~STATE_ATTACHED)

#define SET_DO_INPUT(i) (i->state |= STATE_DO_INPUT)
#define SET_DO_NO_INPUT(i) (i->state &= ~STATE_DO_INPUT)

#define SET_DO_OUTPUT(i) (i->state |= STATE_DO_OUTPUT)
#define SET_DO_NO_OUTPUT(i) (i->state &= ~STATE_DO_OUTPUT)

#define SET_SEND_PAUSED(i) (i->state |= STATE_SEND_PAUSED)
#define SET_SEND_UNPAUSED(i) (i->state &= ~STATE_SEND_PAUSED)

#define SET_RECV_PAUSED(i) (i->state |= STATE_RECV_PAUSED)
#define SET_RECV_UNPAUSED(i) (i->state &= ~STATE_RECV_PAUSED)

#define SET_HANDLE_REDIRECT(i) (i->state |= STATE_HANDLE_REDIRECT)
#define SET_DONT_HANDLE_REDIRECT(i) (i->state &= ~STATE_HANDLE_REDIRECT)

#define SET_NEED_INPUT(i) (i->state |= STATE_NEED_INPUT)
#define SET_NEED_NO_INPUT(i) (i->state &= ~STATE_NEED_INPUT)

#define SET_NEED_OUTPUT(i) (i->state |= STATE_NEED_OUTPUT)
#define SET_NEED_NO_OUTPUT(i) (i->state &= ~STATE_NEED_OUTPUT)

#define SET_DONE_SENDING(i) (i->state |= STATE_DONE_SENDING)
#define SET_NOT_DONE_SENDING(i) (i->state &= ~STATE_DONE_SENDING)

#define SET_SEEN_HEADER_END(i) (i->state |= STATE_SEEN_HEADER_END)
#define SET_SEEN_NO_HEADER_END(i) (i->state &= ~STATE_SEEN_HEADER_END)

#define MAX_LOCAL_ALLOC (1024 * 8)

#define TOLOWER(data) ((data > 0x40 && data < 0x5b) ? data|0x60 : data)

#define likely(x)       __builtin_expect(!!(x), 1)
#define unlikely(x)     __builtin_expect(!!(x), 0)

#define ACQUIRE(lock) (likely(!(__sync_lock_test_and_set(&(lock), 1))))

#define RELEASE(lock) (__sync_lock_release(&(lock)))

const static int candidate_signals[] = { SIGWINCH, SIGTTIN, SIGTTOU };

#define ARRAY_SIZE(x) (sizeof(x)/sizeof((x)[0]))

#if 1//CHTTPC_DEBUG
#define LOG(...) ((void) __android_log_print(ANDROID_LOG_DEBUG, "chttpc", __VA_ARGS__))
#else
#define LOG(...) {}
#endif

JNIEXPORT void _init(void){}

struct curl_hdr {
    struct curl_hdr* ref;
    char header[0];
};


struct curl_data {
    struct curl_slist* outHeaders;
    CURL* curl;
    CURLM* multi;
    JNIEnv* env;
    jbyteArray bufferForReceiving;
    jbyteArray bufferForSending;
    Hashmap* headers;
    void** headerPairs;
    uint32_t headerBufSize;
    uint16_t headerPairCount;
    uint16_t maxHeaderLength;
    uint64_t uploadedCount;
    uint64_t uploadGoal;
    uint32_t state;
    jint readOffset;
    jint countToRead;
    jint readOverflow;
    jint writeOffset;
    jint countToWrite;
    jint connTimeout;
    jint readTimeout;
    i10n_ptr interrupted;
    volatile _Atomic uint32_t busy;
    uint16_t outHeaderCount;
    char errorBuffer[CURL_ERROR_SIZE];
};

static system_property_get getprop;

static jclass wrapper;
static jclass ioException;
static jclass javaString;
static jmethodID threadingCb;

#define ERROR_USE_SYNCHRONIZATION 0
#define ERROR_DNS_FAILURE 1
#define ERROR_NOT_EVEN_HTTP 2
#define ERROR_SOCKET_CONNECT_REFUSED 3
#define ERROR_SOCKET_CONNECT_TIMEOUT 4
#define ERROR_SOCKET_READ_TIMEOUT 5
#define ERROR_RETRY_IMPOSSIBLE 6
#define ERROR_BAD_URL 7
#define ERROR_INTERFACE_BINDING_FAILED 8
#define ERROR_SSL_FAIL 9
#define ERROR_SOCKET_MYSTERY 10
#define ERROR_OOM 11
#define ERROR_PROTOCOL 12
#define ERROR_ILLEGAL_STATE 13
#define ERROR_INTERRUPTED 14
#define ERROR_CLOSED 15

static __attribute__ ((noinline, cold)) void interruption_handler(int signo, siginfo_t* info, void* unused) {
    void* ref = info -> si_value.sival_ptr;

    if (ref  != NULL) {
        *((volatile _Atomic uint32_t*) ref) = 1;
    }
}

static void buffer_read(struct curl_data* ctrl, jobject* buf_, void* buffer, int count) {
    JNIEnv* env = ctrl->env;

    memcpy(buffer, (*env)->GetDirectBufferAddress(env, buf_), (size_t) count);
}

static void buffer_write(struct curl_data* ctrl, jobject* buf_, void* buffer, int count) {
    JNIEnv* env = ctrl->env;

    memcpy((*env)->GetDirectBufferAddress(env, buf_), buffer, (size_t) count);
}

static inline void array_read(struct curl_data* ctrl, jobject* buf_, void* buffer, int count) {
    JNIEnv* env = ctrl->env;

    (*env)->GetByteArrayRegion(env, buf_, ctrl->writeOffset, count, (jbyte*) buffer);
}

static inline void array_write(struct curl_data* ctrl, jobject* buf_, void* ptr, int count) {
    JNIEnv* env = ctrl->env;

    (*env)->SetByteArrayRegion(env, buf_, ctrl->readOffset, count, (jbyte*) ptr);
}

static __attribute__ ((noinline, cold)) void throwInterruptedException(struct curl_data* ctrl, jint count) {
    /*
     allow calling code to do it's own thing
    JNIEnv* env = ctrl->env;
    (*env) -> CallStaticVoidMethod(env, wrapper, threadingCb, NULL, ERROR_INTERRUPTED, count);
    return;
     */
}

static __attribute__ ((noinline, cold)) void throwThreadingException(JNIEnv* env) {
    (*env) -> CallStaticVoidMethod(env, wrapper, threadingCb, NULL, ERROR_USE_SYNCHRONIZATION, 0);
    return;
}

static __attribute__ ((noinline, cold)) void oomThrow(JNIEnv* env) {
    (*env) -> CallStaticVoidMethod(env, wrapper, threadingCb, NULL, ERROR_OOM, 0);
    return;
}

static __attribute__ ((noinline)) void throwTimeout(JNIEnv* env, int transferred) {
    int errorType = transferred > 0 ? ERROR_SOCKET_READ_TIMEOUT : ERROR_SOCKET_CONNECT_TIMEOUT;
    (*env) -> CallStaticVoidMethod(env, wrapper, threadingCb, NULL, errorType, 0);
    return;
}

static __attribute__ ((noinline)) void throwOther(JNIEnv* env, const char* error, int errType) {
    jstring errMsg = (*env) -> NewStringUTF(env, error);
    (*env) -> CallStaticVoidMethod(env, wrapper, threadingCb, errMsg, errType, 0);
    return;
}

static inline void releaseHeaders(struct curl_data* ctrl) {
    for (int i = 0; i < ctrl->headerPairCount; i += 2) {
        free (ctrl->headerPairs[i]);
    }

    ctrl->headerPairCount = 0;
}

static inline bool headers_ensure_capacity(struct curl_data* ctrl, size_t newItemCapacity) {
    if (newItemCapacity <= ctrl -> headerBufSize) {
        return false;
    }

    size_t targetItemCapacity = (size_t) (1.5 * newItemCapacity);

    void* newAddress = realloc(ctrl->headerPairs, sizeof(char*) * 2 * targetItemCapacity);

    if (unlikely(newAddress == NULL)) {
        oomThrow(ctrl->env);

        return true;
    }

    ctrl->headerPairs = newAddress;
    ctrl->headerBufSize = targetItemCapacity;

    return false;
}

// read callbacks


static size_t read_callback(char *buffer, size_t size, size_t nitems, void *instream) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) instream;

    LOG("Read called for %d bytes, space left in buffer: %d, offset: %d", ctrl->countToWrite, size * nitems, ctrl->writeOffset);

    if (ctrl->state & STATE_DONE_SENDING) {
        LOG("Stopping sending");
        SET_SEND_PAUSED(ctrl);
        return 0;
    }

    if (ctrl->countToWrite == 0) {
        LOG("Pausing sending");

        // we have run out of data to send, slow down
        SET_SEND_PAUSED(ctrl);

        return CURL_WRITEFUNC_PAUSE;
    }

    jbyteArray buf_ = ctrl->bufferForSending;

    int written;

    if (buf_ != NULL) {
        int available = ctrl->countToWrite;

        int curlBufferCapacity = size * nitems;

        written = available > curlBufferCapacity ? curlBufferCapacity : available;

        array_read(ctrl, buf_, buffer, written);

        ctrl->writeOffset += written;
    } else {
        // writing a single byte
        written = 1;

        *buffer = (unsigned char) ctrl->writeOffset;
    }

    uint64_t goal = ctrl->uploadGoal;
    if (goal) {
        ctrl->uploadedCount += written;
        if (ctrl->uploadedCount >= goal) {
            // curl will stop the upload itself, but we also need this knowledge for
            // deciding when to stop looping
            SET_SEND_PAUSED(ctrl);
        }
    }

    ctrl->countToWrite -= written;

    return (size_t) written;
}

static size_t eof_callback(char *buffer, size_t size, size_t nitems, void *instream) {
    LOG("Stopping sending");

    struct curl_data* ctrl = (struct curl_data*) (intptr_t) instream;

    SET_SEND_PAUSED(ctrl);

    return 0;
}

// write callbacks

// immediately put the operation on pause
static size_t connect_and_pause_callback(char *buffer, size_t size, size_t nitems, void *userdata) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) userdata;

    SET_RECV_PAUSED(ctrl);

    LOG("pausing for connect");

    return CURL_READFUNC_PAUSE;
}

// purposefully do not read response contents from server
static size_t abort_receive_callback(char *buffer, size_t size, size_t nitems, void *userdata) {
    LOG("Short-circuiting read()");

    return 0;
}

static size_t skip_receive_callback(char *buffer, size_t size, size_t nitems, void *userdata) {
    LOG("Omitting read data");

    return size * nitems;
}

static inline void wsp_trim_start(char** first, char** afterLast) {
    while(*first != *afterLast) {
        if (**first == ' ') ++*first; else break;
    }
}

static inline void wsp_trim_end(char** first, char** afterLast) {
    while(*afterLast != *first) {
        if (*(*afterLast - 1) == '\0'
            || *(*afterLast - 1) == ' '
            || *(*afterLast - 1) == '\r'
            || *(*afterLast - 1) == '\n') {
            --*afterLast;
        } else break;
    }
}

static inline void wsp_trim(char** first, char** afterLast) {
    wsp_trim_start(first, afterLast);
    wsp_trim_end(first, afterLast);
}

size_t header_callback(char *buffer, size_t size, size_t nitems,   void *userdata) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) userdata;

    size_t bufferLength = size * nitems;

    int i;
    for (i = 0; i < bufferLength; ++i) {
        if (buffer[i] == ':') {
            break;
        }
    }

    uint16_t oldHeaderCount = 0;
    bool statusLine = false;

    if (i == bufferLength && buffer[bufferLength - 2] == '\r' && buffer[bufferLength - 1] == '\n') {
        // this is a special-purpose header line (status line or terminating crlf line)

        if (i > 5 && buffer[0] == 'H' && buffer[1] == 'T' && buffer[2] == 'T' && buffer[3] == 'P' && buffer[4] == '/') {
            statusLine = true;

            if (ctrl->state & STATE_SEEN_HEADER_END) {
                // This is a beginning of new request, clear all previously received headers
                LOG("Received new request, resetting headers");
                hashmapClear(ctrl->headers);
                releaseHeaders(ctrl);

                SET_SEEN_NO_HEADER_END(ctrl);
            }

            oldHeaderCount = ctrl->headerPairCount;
            ctrl->headerPairCount = 0;
        } else {
            SET_SEEN_HEADER_END(ctrl);

            LOG("Got to the end of headers");

            return bufferLength;
        }
    }

    char* end = buffer + i;
    wsp_trim_end(&buffer, &end);
    size_t keyLen = end - buffer;

    size_t valueLen = bufferLength - i;
    char* valStart = buffer + i + 1;
    char* valEnd = valStart + valueLen;

    wsp_trim(&valStart, &valEnd);

    valueLen = valEnd - valStart;

    void* newPairPos = malloc(keyLen + sizeof(char*) + valueLen + 2);

    struct curl_hdr* newValuePos = newPairPos;
    char* newHeaderPos = newPairPos + sizeof(char*) + valueLen + 1;

    strncpy(newValuePos->header, valStart, valueLen);
    *(newValuePos->header + valueLen) = '\0';

    strncpy(newHeaderPos, buffer, keyLen);
    *(newHeaderPos + keyLen) = '\0';

    //__android_log_print(ANDROID_LOG_DEBUG, "Curl", "Map size is %d", ctrl->headers->size);

    if (statusLine) {
        goto skip;
    }

    Entry* old = entryGet(ctrl->headers, newHeaderPos);
    if (old == NULL) {
        hashmapPut(ctrl->headers, newHeaderPos, newValuePos);

        newValuePos->ref = newValuePos;
    } else {
        LOG("Piu Piu for '%s'", (char*) old->key);

        newHeaderPos = old->key;

        struct curl_hdr* existing = old->value;

        newValuePos->ref = existing->ref;
        existing->ref = newValuePos;

        old->value = newValuePos;
    }

    if (keyLen > ctrl->maxHeaderLength) {
        ctrl->maxHeaderLength = (uint16_t) keyLen;
    }

    if (valueLen > ctrl->maxHeaderLength) {
        ctrl->maxHeaderLength = (uint16_t) valueLen;
    }
skip:
    if (unlikely(headers_ensure_capacity(ctrl, ctrl->headerPairCount + 1))) {
        return 0;
    }

    int oldEnd = ctrl->headerPairCount * 2;
    void** headerrs = ctrl->headerPairs;

    headerrs[oldEnd] = newValuePos;
    headerrs[oldEnd + 1] = newHeaderPos;

    if (statusLine) {
        ctrl->headerPairCount = oldHeaderCount;
    }

    ctrl->headerPairCount++;

    return bufferLength;
}

static size_t seek_callback(void *userp, curl_off_t offset, int origin) {
    return CURL_SEEKFUNC_CANTSEEK;
}

static size_t write_callback(char *ptr, size_t size, size_t nmemb, void *userdata) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) userdata;

    LOG("Write called for %d bytes, space left in buffer: %d", size * nmemb, ctrl->countToRead);

    if (ctrl->countToRead == 0) {
        SET_RECV_PAUSED(ctrl);

        LOG("Pausing receiving, flags: %d", ctrl->state);

        return CURL_READFUNC_PAUSE;
    }

    LOG("Source offset: %d, target offset: %d", ctrl->readOverflow, ctrl->readOffset);

    JNIEnv* env = ctrl->env;
    jbyteArray buf_ = ctrl->bufferForReceiving;

    int curlPendingDataSize = size * nmemb;

    if (ctrl->readOverflow >= curlPendingDataSize) {
        ctrl->readOverflow -= curlPendingDataSize;

        LOG("Detracting %d bytes from dept", curlPendingDataSize);

        return (size_t) curlPendingDataSize;
    }

    // skip over previously consumed contents
    ptr += ctrl->readOverflow;

    // let's make sure, that we don't go over the buffer limit here...
    int pendingReal = curlPendingDataSize - ctrl->readOverflow;

    int read = 0;

    if (buf_ != NULL) {
        int available = ctrl->countToRead;

        read = available > pendingReal ? pendingReal : available;

        array_write(ctrl, buf_, ptr, read);

        ctrl->readOffset += read;
    } else if (ctrl->countToRead != 0) {
        // consume a single byte
        read = 1;

        ctrl->readOffset = *ptr;
    }

    ctrl->countToRead -= read;

    //__android_log_print(ANDROID_LOG_DEBUG, "Curl", "space left in buffer: %d", ctrl->countToRead);

    if (read == pendingReal) {
        ctrl->readOverflow = 0;

        return (size_t) curlPendingDataSize;
    } else {
        // we are out of space for received data, note the pending buffer size and slow down
        ctrl->readOverflow += read;

        SET_RECV_PAUSED(ctrl);

        LOG("Pausing receiving, flags: %d", ctrl->state);

        return CURL_READFUNC_PAUSE;
    }
}

curl_socket_t opensocket_callback(void *clientp, curlsocktype purpose, struct curl_sockaddr *address) {
    LOG("Opening socket");

    return socket(address->family, address->socktype, address->protocol);
}

static __attribute__ ((noinline,cold)) void handleMultiError(struct curl_data* curl, CURLMcode lastError) {
    LOG("High-level interface error: %d", lastError);

    size_t len = strlen(curl->errorBuffer);

    const char* errorDesc;

    if (len) {
        errorDesc = curl->errorBuffer;

        if (curl->errorBuffer[len - 1] == '\n') {
            curl->errorBuffer[len - 1] = 0;
        }
    } else {
        errorDesc = curl_multi_strerror(lastError);
    }

    JNIEnv* env = curl->env;

    if ((*env) -> ExceptionCheck(env) == JNI_TRUE) {
        // there is already and exception pending, just let it be thrown and log this one
        __android_log_print(ANDROID_LOG_ERROR, "Curl", "Failed to throw, because an exception is pending already: %s", errorDesc);
    } else {
        if (lastError == CURLM_OUT_OF_MEMORY) {
            oomThrow(env);
        } else {
            (*env)->ThrowNew(env, ioException, errorDesc);
        }
    }
}

static __attribute__ ((noinline)) void handleEasyError(struct curl_data* ctrl, CURLcode lastError) {
    LOG("Low-level interface error: %d", lastError);

    if (lastError == CURLE_WRITE_ERROR && !(ctrl->state & STATE_DO_INPUT)) {
        // the caller set doInput to false, so we aborted read
        return;
    }

    size_t len = strlen(ctrl->errorBuffer);

    const char* errorDesc;

    if (len) {
        errorDesc = ctrl->errorBuffer;

        if (ctrl->errorBuffer[len - 1] == '\n') {
            ctrl->errorBuffer[len - 1] = 0;
        }
    } else {
        errorDesc = curl_easy_strerror(lastError);
    }

    JNIEnv* env = ctrl->env;

    if ((*env) -> ExceptionCheck(env) == JNI_TRUE) {
        // there is already and exception pending, just let it be thrown and log this one
        __android_log_print(ANDROID_LOG_ERROR, "Curl", "Failed to throw, because an exception is pending already: %s", errorDesc);
    } else {
        switch (lastError) {
            case CURLE_RECV_ERROR:
            case CURLE_SEND_ERROR:
                throwOther(env, errorDesc, ERROR_SOCKET_MYSTERY);
                break;
            case CURLE_COULDNT_CONNECT:
                throwOther(env, errorDesc, ERROR_SOCKET_CONNECT_REFUSED);
                break;
            case CURLE_WEIRD_SERVER_REPLY:
                throwOther(env, errorDesc, ERROR_NOT_EVEN_HTTP);
                break;
            case CURLE_SSL_CONNECT_ERROR:
            case CURLE_SSL_SHUTDOWN_FAILED:
                throwOther(env, errorDesc, ERROR_SSL_FAIL);
                break;
            case CURLE_URL_MALFORMAT:
            case CURLE_UNSUPPORTED_PROTOCOL:
                throwOther(env, errorDesc, ERROR_BAD_URL);
                break;
            case CURLE_COULDNT_RESOLVE_HOST:
            case CURLE_COULDNT_RESOLVE_PROXY:
                throwOther(env, errorDesc, ERROR_DNS_FAILURE);
                break;
            case CURLE_INTERFACE_FAILED:
                throwOther(env, errorDesc, ERROR_INTERFACE_BINDING_FAILED);
                break;
            case CURLE_SEND_FAIL_REWIND:
                throwOther(env, errorDesc, ERROR_RETRY_IMPOSSIBLE);
                break;
            case CURLE_HTTP2:
            case CURLE_HTTP2_STREAM:
                throwOther(env, errorDesc, ERROR_PROTOCOL);
                break;
            case CURLE_OUT_OF_MEMORY:
                oomThrow(env);
                break;
            default:
                (*env) -> ThrowNew(env, ioException, errorDesc);
        }
    }
}

inline static jclass saveClassRef(const char* name, JNIEnv *env) {
    jclass found = (*env) -> FindClass(env, name);

    if (unlikely(found == NULL)) {
        return NULL;
    }

    return (*env) -> NewGlobalRef(env, found);
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    curl_global_init(CURL_GLOBAL_ALL | CURL_GLOBAL_ACK_EINTR);

    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL Java_net_sf_chttpc_Curl_nativeInit(JNIEnv *env, jclass curlWrapper) {
    void *handle = dlopen("libc.so", RTLD_LAZY);

    if (handle) {
        getprop = (system_property_get) dlsym(handle, "__system_property_get");
    }

    wrapper = (*env) -> NewGlobalRef(env, curlWrapper);
    if (wrapper == NULL) {
        return;
    }

    ioException = saveClassRef("java/io/IOException", env);
    if (ioException == NULL) {
        return;
    }

    javaString = saveClassRef("java/lang/String", env);
    if (javaString == NULL) {
        return;
    }

    threadingCb = (*env) -> GetStaticMethodID(env, curlWrapper, "throwException", "(Ljava/lang/String;II)V");
    if (threadingCb == NULL) {
        return;
    }
}

static int logcat_tracer(CURL *handle, curl_infotype type, char *data, size_t size, void *userp)
{
    char* format;

    switch (type) {
        case CURLINFO_HEADER_IN:
        case CURLINFO_HEADER_OUT:
        case CURLINFO_DATA_OUT:
        case CURLINFO_DATA_IN:
        case CURLINFO_TEXT:
            __android_log_print(ANDROID_LOG_DEBUG, "Curl", "%.*s", size, data);
            break;
        default:
            break;
    }

    return 0;
}

static inline int hashCalc(void* key) {
    size_t len = strlen(key);

    int ch; uint h = len;

    unsigned char* data = (unsigned char*) key;

    size_t i;

    for (i = 0; i < len; i++) {
        ch = *data;

        h = h * 31 + TOLOWER(ch);

        data++;
    }

    return h;
}

static inline bool hashKeyCompare(void* a, void* b) {
    const unsigned char *p1 = (const unsigned char *) a;
    const unsigned char *p2 = (const unsigned char *) b;

    int result;

    if (p1 == p2)
        return true;

    while ((result = (TOLOWER(*p1) - TOLOWER(*p2++)) == 0))
        if (*p1++ == '\0')
            break;

    return result == 0;
}

JNIEXPORT jlong JNICALL Java_net_sf_chttpc_Curl_nativeCreate(JNIEnv *env, jclass type, jint flags) {
    struct curl_data* ctrl = memalign(64u, sizeof(*ctrl));

    CURL* curl = curl_easy_init();
    CURLM* multi = curl_multi_init();

    // we are managing our own timeouts
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, LONG_MAX);

    curl_easy_setopt(curl, CURLOPT_HTTP_VERSION, CURL_HTTP_VERSION_2TLS);

    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_PROXY_SSL_VERIFYPEER, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    curl_easy_setopt(curl, CURLOPT_PROXY_SSL_VERIFYHOST, 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_OPTIONS, CURLSSLOPT_ALLOW_BEAST | CURLSSLOPT_NO_REVOKE);

    if (flags & FLAG_DEBUG) {
        curl_easy_setopt(curl, CURLOPT_VERBOSE, 1L);
        curl_easy_setopt(curl, CURLOPT_DEBUGFUNCTION, &logcat_tracer);
    }

    if (flags & FLAG_RAW_RESPONSE) {
        curl_easy_setopt(curl, CURLOPT_HTTP_TRANSFER_DECODING, 0L);
        curl_easy_setopt(curl, CURLOPT_HTTP_CONTENT_DECODING, 0L);
    }

    curl_easy_setopt(curl, CURLOPT_IPRESOLVE, (flags & FLAG_USE_IPV6) ? CURL_IPRESOLVE_WHATEVER : CURL_IPRESOLVE_V4);
    curl_easy_setopt(curl, CURLOPT_TRANSFER_ENCODING, (flags & FLAG_REQUEST_COMPRESSION) ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_TCP_NODELAY, (flags & FLAG_TCP_NODELAY) ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_KEEP_SENDING_ON_ERROR, (flags & FLAG_SEND_AFTER_ERROR) ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_SSL_FALSESTART, (flags & FLAG_TLS_FALSE_START) ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_TCP_FASTOPEN, (flags & FLAG_TCP_FAST_OPEN) ? 1L : 0L);
    curl_easy_setopt(curl, CURLOPT_TCP_KEEPALIVE, (flags & FLAG_TCP_KEEP_ALIVE) ? 1L : 0L);

    curl_easy_setopt(curl, CURLOPT_NETRC, CURL_NETRC_IGNORED);
    curl_easy_setopt(curl, CURLOPT_SEEKFUNCTION, &seek_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, &write_callback);
    curl_easy_setopt(curl, CURLOPT_READFUNCTION, &read_callback);
    curl_easy_setopt(curl, CURLOPT_OPENSOCKETFUNCTION, &opensocket_callback);
    curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, &header_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, ctrl);
    curl_easy_setopt(curl, CURLOPT_READDATA, ctrl);
    curl_easy_setopt(curl, CURLOPT_HEADERDATA, ctrl);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
    curl_easy_setopt(curl, CURLOPT_PATH_AS_IS, 1L);
    curl_easy_setopt(curl, CURLOPT_SUPPRESS_CONNECT_HEADERS, 1L);
    curl_easy_setopt(curl, CURLOPT_ERRORBUFFER, ctrl->errorBuffer);

    ctrl->curl = curl;
    ctrl->multi = multi;

    ctrl->outHeaders = NULL;
    ctrl->readOverflow = 0;
    ctrl->state = 0;
    ctrl->headerPairCount = 0;
    ctrl->outHeaderCount = 0;
    ctrl->maxHeaderLength = 0;
    ctrl->headerBufSize = 20u;

    ctrl->busy = 0;

    ctrl->headerPairs = malloc(ctrl->headerBufSize * sizeof(char*) * 2);
    ctrl->headers = hashmapCreate(20u, &hashCalc, &hashKeyCompare);

    if (ctrl->headers == NULL || ctrl->headerPairs == NULL) {
        oomThrow(env);
        return 0;
    }

    return (jlong) (intptr_t) ctrl;
}

JNIEXPORT void JNICALL Java_net_sf_chttpc_Curl_reset(JNIEnv *env, jclass type, jlong curlPtr) {
    struct curl_data* ctrl = (struct curl_data *) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return;
    }

    if (ctrl->state & STATE_ATTACHED) {
        curl_multi_remove_handle(ctrl->multi, ctrl->curl);
    }

    ctrl->maxHeaderLength = 0;
    ctrl->uploadedCount = 0;
    ctrl->state = 0;
    ctrl->readOverflow = 0;

    releaseHeaders(ctrl);

    hashmapClear(ctrl->headers);

    RELEASE(ctrl->busy);
}

static void asciiDecode(JNIEnv* env, jstring str, char* dest, jint length) {
    const jchar* chars = (*env) -> GetStringCritical(env, str, NULL);

    for (int i = 0; i < length; ++i) {
        dest[i] = (unsigned char) chars[i];
    }

    (*env) -> ReleaseStringCritical(env, str, chars);
}

static bool checkResult(struct curl_data* handle) {
    int completed = 0;
    int remaining = 1;

    while(remaining) {
        CURLMsg* msg = curl_multi_info_read(handle->multi, &remaining);

        if (msg == NULL || msg->msg != CURLMSG_DONE) {
            return false;
        }

        LOG("curl_multi_info_read returned %d %d", (int) msg->msg, msg->data.result);


        switch (msg->data.result) {
            case CURLE_ABORTED_BY_CALLBACK:
            case CURLE_OK:
                ++completed;
                continue;
            default:
                handleEasyError(handle, msg->data.result);
                return true;
        }
    }

    LOG("Events consumed");

    return completed != 0;
}

static bool curlPerform(struct curl_data* ctrl) {
    int timeout = ctrl->headerPairCount == 0 ? ctrl->connTimeout : ctrl->readTimeout;

    struct timespec timeoutTimestamp, currentTimestamp;

    clock_gettime(CLOCK_MONOTONIC, &timeoutTimestamp);

    int fdEvents = 0;

    do {
        if (*ctrl->interrupted) {
            return false;
        }

        int running;

        LOG("Invoking perform()");

        CURLMcode result = curl_multi_perform(ctrl->multi, &running);

        if (unlikely(result)) {
            handleMultiError(ctrl, result);
            return true;
        }

        if (checkResult(ctrl)) {
            LOG("Has completed connections, bailing");
            return true;
        }

        if ((ctrl->state & STATE_NEED_INPUT && ctrl->state & STATE_RECV_PAUSED) || (ctrl->state & STATE_NEED_OUTPUT
                 && (ctrl->state & STATE_SEND_PAUSED || ctrl->state & STATE_RECV_PAUSED && ctrl->state & STATE_DONE_SENDING))) {
            // nothing to do for now
            LOG("Bailing, state is: %u", ctrl->state);
            return false;
        } else if (!(ctrl->state & STATE_NEED_OUTPUT) && !(ctrl->state & STATE_NEED_INPUT)
                   && (ctrl->state & STATE_SEND_PAUSED || ctrl->state & STATE_RECV_PAUSED
                       || (ctrl->state & STATE_SEEN_HEADER_END && (!(ctrl->state & STATE_HANDLE_REDIRECT) || !hashmapGet(ctrl->headers, "Location"))))) {
            LOG("state is: %u, early headers comsumed", ctrl->state);
            return false;
        } else {
            LOG("state is: %u", ctrl->state);
        }

        if (running == 0) {
            LOG("No running connections, bailing");
            return true;
        }

        if (*ctrl->interrupted) {
            return false;
        }

        long waitTime;
        curl_multi_timeout(ctrl->multi, &waitTime);

        if (timeout < waitTime) {
            waitTime = timeout;
        }

        LOG("Timeout is %d, waiting for %ld", timeout, waitTime);

        if (waitTime > 0) {
            CURLMcode waitResult = curl_multi_wait(ctrl->multi, NULL, 0, waitTime, &fdEvents);

            if (unlikely(waitResult)) {
                handleMultiError(ctrl, waitResult);
                return true;
            }
        }

        LOG("Sockets with events: %d", fdEvents);

        if (fdEvents) {
            timeout = ctrl->headerPairCount == 0 ? ctrl->connTimeout : ctrl->readTimeout;
        } else {
            clock_gettime(CLOCK_MONOTONIC, &currentTimestamp);

            timeout -= ((currentTimestamp.tv_sec - timeoutTimestamp.tv_sec) * 1000);

            int diff_nsec = currentTimestamp.tv_nsec - timeoutTimestamp.tv_nsec;

            if (diff_nsec < 0) {
                timeout += 1000;
                timeout -= ((diff_nsec + 1000000000) / 1000000);
            }

            if (timeout <= 0) {
                LOG("Remaining time is %d, bailing", timeout);
                throwTimeout(ctrl->env, ctrl->headerPairCount);
                return true;
            }

            timeoutTimestamp = currentTimestamp;
        }
    } while (true);
}

#define HTTP_TYPE_GET 0
#define HTTP_TYPE_POST 1
#define HTTP_TYPE_PUT 2
#define HTTP_TYPE_HEAD 3
#define HTTP_TYPE_CUSTOM 4

JNIEXPORT jcharArray JNICALL Java_net_sf_chttpc_Curl_nativeConfigure(JNIEnv *env, jclass type,
                                                               jlong curlPtr,
                                                               jlong i10nPtr,
                                                               jlong fixedLength,
                                                               jcharArray url,
                                                               jstring method,
                                                               jstring proxy,
                                                               jstring dns,
                                                               jstring ifName,
                                                               jint urlLength,
                                                               jint readTimeout,
                                                               jint connTimeout,
                                                               jint proxyType,
                                                               jint reqType,
                                                               jint chunkLength,
                                                               jboolean followRedirects,
                                                               jboolean doInput,
                                                               jboolean doOutput) {
    LOG("++++++++nativeConfigure");

    struct curl_data* ctrl = (struct curl_data *) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return NULL;
    }

    ctrl->env = env;
    ctrl->countToRead = 0;
    ctrl->countToWrite = 0;
    ctrl->interrupted = (i10n_ptr) (intptr_t) i10nPtr;
    ctrl->errorBuffer[0] = 0;

    CURL* curl = ctrl->curl;

    jchar* urlStr = (*env) -> GetPrimitiveArrayCritical(env, url, NULL);
    char* urlBuffer = malloc((size_t) (urlLength + 1));
    if (unlikely(urlBuffer == NULL)) {
        oomThrow(env);
        goto whoops;
    }

    int i;
    for (i = 0; i < urlLength; ++i) {
        urlBuffer[i] = (unsigned char) urlStr[i];
    }
    urlBuffer[i] = '\0';

    curl_easy_setopt(curl, CURLOPT_URL, urlBuffer);

    (*env) -> ReleasePrimitiveArrayCritical(env, url, urlStr, JNI_ABORT);

    if (followRedirects == JNI_TRUE) {
        SET_HANDLE_REDIRECT(ctrl);

        curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    } else {
        SET_DONT_HANDLE_REDIRECT(ctrl);

        curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 0L);
    }

    if (proxy != NULL) {
        const char* proxyStr = (*env) -> GetStringUTFChars(env, proxy, NULL);

        switch (proxyType) {
            case 2:
                curl_easy_setopt(curl, CURLOPT_PROXYTYPE, CURLPROXY_HTTPS);
                // fall-through
            case 1:
                curl_easy_setopt(curl, CURLOPT_PROXY, proxyStr);
                break;
            case 3:
                curl_easy_setopt(curl, CURLOPT_PRE_PROXY, proxyStr);
                break;
            case 4:
                curl_easy_setopt(curl, CURLOPT_PROXYTYPE, CURLPROXY_SOCKS5_HOSTNAME);

                curl_easy_setopt(curl, CURLOPT_PROXY, proxyStr);

                break;
            default:
                break;
        }

        (*env) -> ReleaseStringUTFChars(env, proxy, proxyStr);
    } else {
        // disable proxy, don't bother to read environment variables
        curl_easy_setopt(curl, CURLOPT_PROXY, "");
    }

    // If doOutput is set, then treat requests as PUT.
    // Otherwise if asked for HEAD and doInput is not set, treat them as HEAD.
    // Otherwise treat them  as GET.
    // Treating all requests with doInput == false as HEAD is very bad idea,
    // because it will result in botched retries because of Keep-Alive
    // Never treat requests as POST, because POST handling in curl
    // is kind of hot garbage
    if (doOutput) {
        curl_easy_setopt(curl, CURLOPT_UPLOAD, 1L);
    } else if (reqType == HTTP_TYPE_HEAD && !doInput) {
        curl_easy_setopt(curl, CURLOPT_NOBODY, 1L);
    } else {
        curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
    }

    switch (reqType) {
        case HTTP_TYPE_GET:
            curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "GET");
            break;
        case HTTP_TYPE_PUT:
            curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "PUT");
            break;
        case HTTP_TYPE_HEAD:
            curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "HEAD");
            break;
        case HTTP_TYPE_POST:
            curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "POST");
            break;
        default: {
            jint strLength = (*env)->GetStringLength(env, method);

            char reqMethodChars[strLength + 1];

            asciiDecode(env, method, reqMethodChars, strLength);

            reqMethodChars[strLength] = '\0';

            curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, reqMethodChars);

            break;
        }
    }

    if (doOutput && fixedLength) {
        SET_DO_OUTPUT(ctrl);

        curl_easy_setopt(curl, CURLOPT_INFILESIZE_LARGE, (curl_off_t) fixedLength);

        ctrl->uploadGoal = (uint64_t) fixedLength;
    } else {
        SET_DO_NO_OUTPUT(ctrl);

        curl_easy_setopt(curl, CURLOPT_INFILESIZE_LARGE, (curl_off_t) -1);

        ctrl->uploadGoal = 0;
    }

    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, ctrl->outHeaders);

    if (dns != NULL) {
        jint strLength = (*env) -> GetStringLength(env, dns);

        char dnsChars[strLength + 1];

        asciiDecode(env, dns, dnsChars, strLength);

        dnsChars[strLength] = '\0';

        curl_easy_setopt(curl, CURLOPT_DNS_SERVERS, dnsChars);
    } else {
        curl_easy_setopt(curl, CURLOPT_DNS_SERVERS, NULL);
    }

    if (ifName != NULL) {
        jint strLength = (*env) -> GetStringLength(env, ifName);

        size_t bufferLength = 4 + 2 * (size_t) strLength;

        char* tempStr = calloc(bufferLength, sizeof(char));

        strncpy(tempStr, "if!", 3);

        (*env) -> GetStringUTFRegion(env, ifName, 0, strLength, tempStr + 3);

        curl_easy_setopt(curl, CURLOPT_INTERFACE, tempStr);

        free(tempStr);
    } else {
        curl_easy_setopt(curl, CURLOPT_INTERFACE, NULL);
    }

    ctrl->readTimeout = readTimeout <= 0 ? INT32_MAX : readTimeout;
    ctrl->connTimeout = connTimeout <= 0 ? INT32_MAX : connTimeout;

    if (doInput) {
        SET_DO_INPUT(ctrl);

        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, &write_callback);
    } else {
        SET_DO_NO_INPUT(ctrl);

        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, doOutput ? &skip_receive_callback : &abort_receive_callback);
    }

    if (doOutput) {
        curl_easy_setopt(curl, CURLOPT_READFUNCTION, &read_callback);
    }

    if (!(ctrl->state & STATE_ATTACHED)) {
        curl_multi_add_handle(ctrl->multi, curl);

        SET_ATTACHED(ctrl);
    }

    if (*ctrl->interrupted) {
        throwInterruptedException(ctrl, 0);
        goto enough;
    }

    LOG("before 'perform'");

    int running;
    const CURLMcode connectResult = curl_multi_perform(ctrl->multi, &running);

    LOG("after 'perform'");

    switch (connectResult) {
        case CURLM_OK:
            break;
        default:
            handleMultiError(ctrl, connectResult);
            goto enough;
    }

    if (!running && checkResult(ctrl)) {
        goto enough;
    }

    LOG("Comsuming headers");

    if (curlPerform(ctrl)) {
        goto enough;
    }

    if (*ctrl->interrupted) {
        throwInterruptedException(ctrl, 0);
    }

    if (followRedirects) {
        char *effectiveUrl = NULL;
        curl_easy_getinfo(ctrl->curl, CURLINFO_EFFECTIVE_URL, &effectiveUrl);

        if (effectiveUrl && strcmp(effectiveUrl, urlBuffer)) {
            jsize bufferLen = (*env) -> GetArrayLength(env, url);
            size_t newUrlLen = strlen(effectiveUrl);

            if (newUrlLen > bufferLen) {
                url = (*env)->NewCharArray(env, newUrlLen);
            }

            jchar *newUrlStr = (*env)->GetPrimitiveArrayCritical(env, url, NULL);

            for (int j = 0; j < newUrlLen; ++j) {
                newUrlStr[j] = (jchar) effectiveUrl[j];
            }

            if (newUrlLen < bufferLen) {
                newUrlStr[newUrlLen] = (jchar) '\0';
            }

            (*env)->ReleasePrimitiveArrayCritical(env, url, newUrlStr, 0);
        }
    }

enough:
    free(urlBuffer);
whoops:
    RELEASE(ctrl->busy);

    return url;
}

JNIEXPORT void JNICALL Java_net_sf_chttpc_Curl_dispose(JNIEnv *env, jclass type, jlong curlPtr) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (ctrl->state & STATE_ATTACHED) {
        curl_multi_remove_handle(ctrl->multi, ctrl->curl);
    }

    curl_easy_cleanup(ctrl->curl);

    curl_multi_cleanup(ctrl->multi);

    if (ctrl->outHeaders != NULL) {
        curl_slist_free_all(ctrl->outHeaders);
    }

    hashmapFree(ctrl->headers);

    releaseHeaders(ctrl);

    free(ctrl->headerPairs);

    free(ctrl);
}

JNIEXPORT jint JNICALL Java_net_sf_chttpc_Curl_read(JNIEnv *env, jclass type, jlong curlPtr,
                                                    jlong i10nPtr, jobject buf_, jint off, jint count) {
    LOG("++++++++nativeRead");

    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return -1;
    }

    jint retVal = -1;

    SET_NEED_INPUT(ctrl);

    ctrl->env = env;
    ctrl->readOffset = off;
    ctrl->countToRead = count;
    ctrl->countToWrite = 0;
    ctrl->bufferForReceiving = buf_;
    ctrl->interrupted = (i10n_ptr) (intptr_t) i10nPtr;
    ctrl->errorBuffer[0] = 0;

    if (ctrl->state & STATE_RECV_PAUSED) {
        SET_RECV_UNPAUSED(ctrl);

        CURLcode result = curl_easy_pause(ctrl->curl, CURLPAUSE_RECV_CONT);

        if (result) {
            handleEasyError(ctrl, result);
            goto enough;
        }
    }

    //__android_log_print(ANDROID_LOG_DEBUG, "Curl", "%s", "before perform inside read()");

    if (*ctrl->interrupted) {
        throwInterruptedException(ctrl, count - ctrl->countToRead);
        goto success;
    }

    bool perform = curlPerform(ctrl);

    if (*ctrl->interrupted) {
        throwInterruptedException(ctrl, count - ctrl->countToRead);
        goto success;
    }

    if (perform && ctrl->countToRead > 0 && ctrl->countToRead == count) {
        LOG("returning -1 from read()");
        goto enough;
    }

    //__android_log_print(ANDROID_LOG_DEBUG, "Curl", "returning %d from read()", count - ctrl->countToRead);
success:
    retVal = count == 1 ? ctrl->readOffset : count - ctrl->countToRead;

enough:
    SET_NEED_NO_INPUT(ctrl);

    RELEASE(ctrl->busy);

    return retVal;
}

JNIEXPORT jint JNICALL Java_net_sf_chttpc_Curl_write(JNIEnv *env, jclass type, jlong curlPtr,
                                                     jlong i10nPtr, jbyteArray buf_, jint off, jint count) {
    LOG("++++++++nativeWrite");

    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return -1;
    }

    uint64_t goal = ctrl->uploadGoal;
    if (goal && ctrl->uploadedCount >= goal) {
        throwOther(env, "", ERROR_CLOSED);
        return -1;
    }

    SET_NEED_OUTPUT(ctrl);

    ctrl->env = env;
    ctrl->writeOffset = off;
    ctrl->countToWrite = count;
    ctrl->countToRead = 0;
    ctrl->bufferForSending = buf_;
    ctrl->interrupted = (i10n_ptr) (intptr_t) i10nPtr;
    ctrl->errorBuffer[0] = 0;

    if (ctrl->state & STATE_SEND_PAUSED) {
        SET_SEND_UNPAUSED(ctrl);

        CURLcode result = curl_easy_pause(ctrl->curl, CURLPAUSE_SEND_CONT);

        if (result) {
            handleEasyError(ctrl, result);
            goto enough;
        }
    }

    if (*ctrl->interrupted) {
        throwInterruptedException(ctrl, count - ctrl->countToWrite);
        goto enough;
    }

    if (ctrl->countToWrite > 0) {
        curlPerform(ctrl);
    }

    if (*ctrl->interrupted) {
        throwInterruptedException(ctrl, count - ctrl->countToWrite);
        goto enough;
    }

enough:
    SET_NEED_NO_OUTPUT(ctrl);
    RELEASE(ctrl->busy);

    return count - ctrl->countToWrite;
}

JNIEXPORT void JNICALL Java_net_sf_chttpc_Curl_closeOutput(JNIEnv *env, jclass type, jlong curlPtr, jlong i10nPtr) {
    LOG("++++++++nativeClose");

    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return;
    }

    ctrl->env = env;
    ctrl->countToRead = 0;
    ctrl->countToWrite = 0;
    ctrl->interrupted = (i10n_ptr) (intptr_t) i10nPtr;
    ctrl->errorBuffer[0] = 0;

    SET_NEED_OUTPUT(ctrl);
    SET_DONE_SENDING(ctrl);

    curl_easy_setopt(ctrl->curl, CURLOPT_READFUNCTION, &eof_callback);

    if (ctrl->state & STATE_SEND_PAUSED) {
        SET_SEND_UNPAUSED(ctrl);

        CURLcode result = curl_easy_pause(ctrl->curl, CURLPAUSE_SEND_CONT);

        if (result) {
            handleEasyError(ctrl, result);
            goto enough;
        }
    }

    //__android_log_print(ANDROID_LOG_DEBUG, "Curl", "Calling perform()");

    if (!(ctrl->state & STATE_SEND_PAUSED)) {
        curlPerform(ctrl);
    }

enough:
    SET_NEED_NO_OUTPUT(ctrl);
    RELEASE(ctrl->busy);
}

static inline jstring headerToString(JNIEnv *env, jchar* tempBuffer, const char* str, size_t len) {
    // since headers are guaranteed to be ASCII, convert to UTF-16 by shortcut
    for (int p = 0; p < len; ++p) {
        tempBuffer[p] = (jchar) str[p];
    }

    return (*env) -> NewString(env, tempBuffer, len);
}

static jobjectArray getResponseHeaders(struct curl_data* ctrl, JNIEnv *env) {
    if (ctrl->headers->size == 0) {
        return NULL;
    }

    int32_t tempBufferSize = 0;
    jchar* tempBuffer;

    if (ctrl->maxHeaderLength <= MAX_LOCAL_ALLOC) {
        tempBuffer = alloca(ctrl->maxHeaderLength * 2);
    } else {
        tempBufferSize = ctrl->maxHeaderLength * 2;
        tempBuffer = malloc((size_t) tempBufferSize);

        if (unlikely(tempBuffer == NULL)) {
            oomThrow(env);
            return NULL;
        }
    }

    jint localFrameRes = -1;

    Hashmap *headers = ctrl->headers;

    jsize arrayCapacity = headers->size * 3 + (ctrl->headerPairCount - 1 - headers->size) - 1;

    jobjectArray strArray = (*env) -> NewObjectArray(env, arrayCapacity, javaString, NULL);
    if (strArray == NULL) {
        goto cleanup;
    }

    if (ctrl->headerPairCount > 5) {
        // at this rate we might run out of local references to store headers
        // rather than wasting CPU time on calls to DeleteGlobalRef, let's batch
        localFrameRes = (*env) -> PushLocalFrame(env, ctrl->headerPairCount + 1);
        if (localFrameRes) {
            goto cleanup;
        }
    }

    int hdrPos = 0;
    for (int i = 0; i < headers->bucketCount; i++) {
        Entry* entry = headers->buckets[i];
        while (entry != NULL) {
            Entry *next = entry->next;

            size_t keyLen = strlen(entry->key);

            jstring strHdrKey = headerToString(env, tempBuffer, entry->key, keyLen);
            if (unlikely(strHdrKey == NULL)) {
                strArray = NULL;
                goto cleanup;
            }

            (*env) -> SetObjectArrayElement(env, strArray, hdrPos++, strHdrKey);

            struct curl_hdr* hdrValues = ((struct curl_hdr*) entry->value)->ref;
            void* first = hdrValues;

            do {
                char* valuePtr = hdrValues->header;

                size_t valLen = strlen(valuePtr);

                jstring strHdrValue = headerToString(env, tempBuffer, valuePtr, valLen);
                if (unlikely(strHdrValue == NULL)) {
                    strArray = NULL;
                    goto cleanup;
                }

                LOG("Setting element at %d", hdrPos);

                (*env) -> SetObjectArrayElement(env, strArray, hdrPos++, strHdrValue);

                hdrValues = hdrValues->ref;
            }
            while (hdrValues != first);

            ++hdrPos;

            entry = next;
        }
    }

cleanup:
    if (localFrameRes == 0) {
        (*env) -> PopLocalFrame(env, NULL);
    }

    if (tempBufferSize != 0) {
        free(tempBuffer);
    }

    return strArray;
}

static jobjectArray getRequestHeaders(struct curl_data* ctrl, JNIEnv *env) {
    jsize arrayCapacity = ctrl->outHeaderCount * 2;

    if (ctrl->outHeaders == NULL || arrayCapacity <= 0) {
        return NULL;
    }

    int32_t tempBufferSize = 4 * 1024;
    jchar* tempBuffer = malloc((size_t) tempBufferSize);

    if (unlikely(tempBuffer == NULL)) {
        oomThrow(env);
        return NULL;
    }

    jint localFrameRes = -1;

    jobjectArray strArray = (*env) -> NewObjectArray(env, arrayCapacity, javaString, NULL);
    if (strArray == NULL) {
        goto cleanup;
    }

    if (ctrl->outHeaderCount > 5) {
        // at this rate we might run out of local references to store headers
        // rather than wasting CPU time on calls to DeleteGlobalRef, let's batch
        localFrameRes = (*env) -> PushLocalFrame(env, ctrl->headerPairCount + 1);
        if (localFrameRes) {
            goto cleanup;
        }
    }

    int actualCount = 0;
    struct curl_slist* entry = ctrl->outHeaders;

    for (int i = 0; i < arrayCapacity; i += 2, entry = entry->next) {
        if (entry == NULL) {
            break;
        }

        LOG("%s", entry->data);

        size_t entryLen = strlen(entry->data);
        size_t keyLen;

        char *keyEnd = strchr(entry->data, ':');
        if (keyEnd) {
            if (keyEnd[1] == '\0') {
                // "disable default header" stub
                continue;
            }

            keyLen = keyEnd - entry->data;
        } else {
            // empty header
            keyLen = entryLen - 1;
        }

        size_t valueLen = entryLen - keyLen - 1;

        size_t maxLen = 2 * (keyLen > valueLen ? keyLen : valueLen);
        if (maxLen >= tempBufferSize) {
            tempBuffer = realloc(tempBuffer, maxLen);
        }

        jstring strHdrKey = headerToString(env, tempBuffer, entry->data, keyLen);
        if (unlikely(strHdrKey == NULL)) {
            strArray = NULL;
            goto cleanup;
        }

        (*env)->SetObjectArrayElement(env, strArray, actualCount * 2, strHdrKey);

        jstring strHdrValue = headerToString(env, tempBuffer, keyEnd + 1, valueLen);
        if (unlikely(strHdrValue == NULL)) {
            strArray = NULL;
            goto cleanup;
        }

        (*env)->SetObjectArrayElement(env, strArray, actualCount * 2 + 1, strHdrValue);

        ++actualCount;
    }

cleanup:
    if (localFrameRes == 0) {
        (*env) -> PopLocalFrame(env, NULL);
    }

    free(tempBuffer);

    return strArray;
}

JNIEXPORT jobjectArray JNICALL Java_net_sf_chttpc_Curl_getHeaders(JNIEnv *env, jclass type, jlong curlPtr, jboolean outHeaders) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return NULL;
    }

    jobjectArray strArray = outHeaders == JNI_TRUE ? getResponseHeaders(ctrl, env) : getRequestHeaders(ctrl, env);

    RELEASE(ctrl->busy);

    return strArray;
}

JNIEXPORT jstring JNICALL Java_net_sf_chttpc_Curl_getDnsCompat(JNIEnv *env, jclass type) {
    if (!getprop) {
        return NULL;
    }

    char prop_name[PROP_VALUE_MAX + 1];

    getprop("net.dns1", prop_name);

    return (*env) -> NewStringUTF(env, prop_name);
}

static char* getSpecialHeader(struct curl_data* ctrl, jint n) {
    if (n >= ctrl->headerPairCount) {
        return NULL;
    }

    if (n > 0) {
        // header value, non-negative, goes first
        struct curl_hdr* hdr = ctrl->headerPairs[2 * n];

        return hdr->header;
    } else {
        // header key, negative or zero (status), goes second
        return ctrl->headerPairs[-n * 2 + 1];
    }
}

static const char* getHeader(JNIEnv *env, struct curl_data* ctrl, jstring key, jint l) {
    if (ctrl->headerPairCount == 0) {
        return NULL;
    }

    const char* result;

    if (key != NULL) {
        char* localKey = alloca(l + 1);

        // since headers are guaranteed to be ASCII, convert to UTF-16 by shortcut
        const jchar* strKey = (*env) -> GetStringCritical(env, key, NULL);

        for (int p = 0; p < l; ++p) {
            localKey[p] = (unsigned char) strKey[p];
        }

        (*env) -> ReleaseStringCritical(env, key, strKey);

        localKey[l] = '\0';

        LOG("Searching for %s within %d items", localKey, ctrl->headers->size);

        struct curl_hdr* found = hashmapGet(ctrl->headers, localKey);

        if (found == NULL) {
            LOG("Not found :(");

            return NULL;
        }

        result = found->header;
    } else {
        result = getSpecialHeader(ctrl, l);
    }

    return result;
}

JNIEXPORT jlong JNICALL Java_net_sf_chttpc_Curl_intHeader(JNIEnv *env, jclass type, jlong curlPtr, jlong defaultValue, jstring key, jint l) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return -1;
    }

    jlong resultInt = -1;

    if (key == NULL) {
        long result;
        curl_easy_getinfo(ctrl->curl, CURLINFO_RESPONSE_CODE, &result);
        if (result != 0)  resultInt = result;
        goto success;
    }

    const char* result = getHeader(env, ctrl, key, l);
    if (result == NULL) {
        goto enough;
    }

    char* failCheck = NULL;
    resultInt = strtoll(result, &failCheck, 10);

    if (failCheck == NULL || *failCheck != '\0') {
        goto enough;
    }

success:
    RELEASE(ctrl->busy);

    return resultInt;

enough:
    RELEASE(ctrl->busy);

    return defaultValue;
}

JNIEXPORT jstring JNICALL Java_net_sf_chttpc_Curl_header(JNIEnv *env, jclass type, jlong curlPtr, jstring key, jint l) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    jstring resultString = NULL;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return NULL;
    }

    const char* result = getHeader(env, ctrl, key, l);

    if (result != NULL) {
        size_t resLen = strlen(result);

        if (resLen <= MAX_LOCAL_ALLOC) {
            jchar* tempResBuf = alloca(resLen * 2);
            resultString = headerToString(env, tempResBuf, result, resLen);
        } else {
            resultString = (*env)->NewStringUTF(env, result);
        }
    }

    RELEASE(ctrl->busy);

    return resultString;
}

// WARNING: this uses knowledge of internal details of curl_slist
// it might be laughable, but in case curl changes the way it's linked list is allocated,
// this code might break...
JNIEXPORT void JNICALL Java_net_sf_chttpc_Curl_setHeader(JNIEnv *env, jclass type, jlong curlPtr, jstring key_, jstring value_, jint kLen, jint valLen) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return;
    }

    char* localBuffer;

    bool hasToFree = false;

    localBuffer = malloc((size_t) (kLen + valLen + 2));
    if (unlikely(localBuffer == NULL)) {
        oomThrow(env);
        goto enough;
    }

    hasToFree = true;

    const jchar *key = (*env)->GetStringCritical(env, key_, NULL);

    int p;
    for (p = 0; p < kLen; ++p) {
        localBuffer[p] = (unsigned char) key[p];
    }

    (*env)->ReleaseStringCritical(env, key_, key);

    char sep = (char) (valLen == 0 ? ';' : ':');

    localBuffer[p++] = sep;

    char** newHeader = NULL;

    struct curl_slist* lastHdr = ctrl->outHeaders;
    struct curl_slist* nextHdr = lastHdr;

    while (nextHdr != NULL) {
        if (!strncasecmp(localBuffer, nextHdr->data, (size_t) kLen)) {
            if (value_ == NULL) {
                free(nextHdr->data);

                if (ctrl->outHeaders != nextHdr) {
                    lastHdr->next = nextHdr->next;
                } else {
                    ctrl->outHeaders = NULL;
                    lastHdr = NULL;
                    break;
                }

                ctrl->outHeaderCount--;

                free(nextHdr);
            } else {
                newHeader = &nextHdr->data;
                lastHdr = nextHdr;
            }
        } else {
            lastHdr = nextHdr;
        }

        nextHdr = lastHdr->next;
    }

    if (valLen < 0) {
        localBuffer[p] = '\0';
        ctrl->outHeaders = curl_slist_append(ctrl->outHeaders, localBuffer);
        goto enough;
    }

    if (newHeader != NULL) {
        free(*newHeader);
    } else {
        struct curl_slist* newEntry = calloc(1u, sizeof(struct curl_slist));

        if (lastHdr == NULL) {
            ctrl->outHeaders = newEntry;
        } else {
            lastHdr->next = newEntry;
        }

        newHeader = &newEntry->data;

        ctrl->outHeaderCount++;
    }

insert:
    if (valLen != 0) {
        const jchar *value = (*env)->GetStringCritical(env, value_, NULL);

        for (int i = 0; p < kLen + 1 + valLen; ++p, ++i) {
            localBuffer[p] = (unsigned char) value[i];
        }

        (*env)->ReleaseStringCritical(env, value_, value);
    }

    localBuffer[p] = '\0';

    *newHeader = localBuffer;
    hasToFree = false;

enough:
    if (hasToFree) {
        free(localBuffer);
    }

    RELEASE(ctrl->busy);
}

JNIEXPORT void JNICALL Java_net_sf_chttpc_Curl_addHeader(JNIEnv *env, jclass type, jlong curlPtr, jstring key_, jstring value_, jint kLen, jint valLen) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return;
    }

    char* localBuffer;

    bool hasToFree = false;

    if (kLen + valLen + 1 < MAX_LOCAL_ALLOC) {
        localBuffer = alloca((size_t) (kLen + valLen + 2));
    } else {
        localBuffer = malloc((size_t) (kLen + valLen + 2));

        if (unlikely(localBuffer == NULL)) {
            oomThrow(env);
            goto enough;
        }

        hasToFree = true;
    }

    const jchar *key = (*env)->GetStringCritical(env, key_, NULL);
    const jchar *value = (*env)->GetStringCritical(env, value_, NULL);

    int p = 0;
    for (; p < kLen; ++p) {
        localBuffer[p] = (unsigned char) key[p];
    }

    localBuffer[p++] = (char) (valLen == 0 ? ';' : ':');

    for (int i = 0; p < kLen + 1 + valLen; ++p, ++i) {
        localBuffer[p] = (unsigned char) value[i];
    }

    localBuffer[p] = '\0';

    (*env)->ReleaseStringCritical(env, key_, key);
    (*env)->ReleaseStringCritical(env, value_, value);

    ctrl->outHeaders = curl_slist_append(ctrl->outHeaders, localBuffer);

    ctrl->outHeaderCount++;
enough:
    if (hasToFree) {
        free(localBuffer);
    }

    RELEASE(ctrl->busy);
}

JNIEXPORT jstring JNICALL Java_net_sf_chttpc_Curl_outHeader(JNIEnv *env, jclass type, jlong curlPtr, jstring key_, jint kLen) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return NULL;
    }

    jstring result = NULL;

    if (ctrl->outHeaderCount == 0) {
        goto enough;
    }

    char* localBuffer;

    bool hasToFree = false;

    if (kLen <= MAX_LOCAL_ALLOC) {
        localBuffer = alloca((size_t) (kLen + 1));
    } else {
        localBuffer = malloc((size_t) (kLen + 1));
        hasToFree = true;
    }

    const jchar *key = (*env)->GetStringCritical(env, key_, NULL);

    size_t i;
    for (i = 0; i < kLen; ++i) {
        localBuffer[i] = (unsigned char) key[i];
    }
    localBuffer[i] = '\0';

    (*env)->ReleaseStringCritical(env, key_, key);

    struct curl_slist* next = ctrl->outHeaders;

    do {
        if (!strncasecmp(localBuffer, next->data, i) && (next->data[kLen] == ':' || next->data[kLen] == ';')) {
            result = (*env) ->NewStringUTF(env, next->data + kLen + 1);
            break;
        }

        next = next->next;
    } while (next != NULL);

    if (hasToFree) {
        free(localBuffer);
    }

enough:
    RELEASE(ctrl->busy);

    return result;
}

JNIEXPORT void JNICALL Java_net_sf_chttpc_Curl_setOptionInt(JNIEnv *env, jclass type, jlong curlPtr, jlong value, jint option) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return;
    }

    enum OPTIONS opt = (enum OPTIONS) option;

    long capped = value > LONG_MAX ? LONG_MAX : ((long) value);

    switch (opt) {
        case KEEPALIVE_PERIOD:
            curl_easy_setopt(ctrl->curl, CURLOPT_TCP_KEEPINTVL, capped);
            curl_easy_setopt(ctrl->curl, CURLOPT_TCP_KEEPIDLE, capped);
            break;
        case CONTINUE_TIMEOUT:
            curl_easy_setopt(ctrl->curl, CURLOPT_EXPECT_100_TIMEOUT_MS, capped);
            break;
        case CONN_CACHE_SIZE:
            curl_easy_setopt(ctrl->curl, CURLMOPT_MAXCONNECTS, capped);
            break;
        case MAX_REDIRECT_COUNT:
            curl_easy_setopt(ctrl->curl, CURLOPT_MAXREDIRS, capped);
            break;
    }

    RELEASE(ctrl->busy);
}

JNIEXPORT void JNICALL Java_net_sf_chttpc_Curl_clearHeaders(JNIEnv *env, jclass type, jlong curlPtr) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    if (!ACQUIRE(ctrl->busy)) {
        throwThreadingException(env);
        return;
    }

    if (ctrl->outHeaders != NULL) {
        curl_slist_free_all(ctrl->outHeaders);
        ctrl->outHeaders = NULL;
    }

    ctrl->outHeaderCount = 0;

    RELEASE(ctrl->busy);
}

JNIEXPORT void JNICALL Java_net_sf_chttpc_Curl_getLastFd(JNIEnv *env, jclass type, jlong curlPtr, jint fd) {
    struct curl_data* ctrl = (struct curl_data*) (intptr_t) curlPtr;

    curl_socket_t s[2];
    s[0] = CURL_SOCKET_BAD;
    s[1] = CURL_SOCKET_BAD;

    multi_getsock(ctrl->curl, s, 2);

    if (s[0] == CURL_SOCKET_BAD || s[1] != CURL_SOCKET_BAD) {
        (*env) -> ThrowNew(env, ioException, "Unable to obtain socket");
        return;
    }

    if (TEMP_FAILURE_RETRY(dup2(s[0], fd)) == -1) {
        (*env) -> ThrowNew(env, ioException, "Failed to copy file descriptor");
    }
}