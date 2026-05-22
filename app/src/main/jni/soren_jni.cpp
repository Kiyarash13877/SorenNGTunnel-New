/*
 * soren_jni.cpp — Real VpnService.protect() bridge
 *
 * PROTECT FLOW (prevents VPN routing loops):
 *   SorenVpnService.onCreate()
 *     → jni.registerProtectCallback(socketProtector)
 *     → nativeRegisterProtectCallback(env, protector_obj)
 *     → stores GlobalRef + SocketProtector.protectFd(I)Z method ID
 *
 *   Any code needing to protect a socket:
 *     → jni.protectFd(fd)   [Kotlin]  OR
 *     → real_protect_fd(fd) [C++]
 *     → JVM callback → SocketProtector.protectFd(fd) → VpnService.protect(fd)
 *
 *   SorenVpnService.onDestroy()
 *     → jni.unregisterProtectCallback()
 *     → DeleteGlobalRef, clears method ID
 */
#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <errno.h>
#include <string.h>
#include <pthread.h>

#define TAG  "SorenJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

extern "C" int soren_fd_valid(int fd);
extern "C" int soren_set_nonblock(int fd);

static pthread_mutex_t g_lock      = PTHREAD_MUTEX_INITIALIZER;
static JavaVM*          g_jvm      = nullptr;
static jobject          g_protobj  = nullptr;
static jmethodID        g_protmid  = nullptr;
static volatile int     g_tun_fd   = -1;
static volatile int     g_running  = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_jvm = vm;
    LOGI("JNI_OnLoad v3.0");
    return JNI_VERSION_1_6;
}

static bool attach_env(JNIEnv** env, bool* attached) {
    *attached = false;
    if (!g_jvm) return false;
    int st = g_jvm->GetEnv((void**)env, JNI_VERSION_1_6);
    if (st == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(env, nullptr) != JNI_OK) return false;
        *attached = true; return true;
    }
    return st == JNI_OK;
}

static bool real_protect(int fd) {
    if (!soren_fd_valid(fd)) { LOGW("protect: invalid fd=%d", fd); return false; }
    pthread_mutex_lock(&g_lock);
    jobject   obj = g_protobj;
    jmethodID mid = g_protmid;
    JavaVM*   jvm = g_jvm;
    pthread_mutex_unlock(&g_lock);
    if (!jvm || !obj || !mid) { LOGW("protect: no JVM callback for fd=%d", fd); return false; }
    JNIEnv* env; bool att;
    if (!attach_env(&env, &att)) { LOGE("protect: attach failed"); return false; }
    jboolean r = env->CallBooleanMethod(obj, mid, (jint)fd);
    if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); r = JNI_FALSE; }
    if (att) jvm->DetachCurrentThread();
    if (!r) LOGW("VpnService.protect(%d) returned false", fd);
    return r == JNI_TRUE;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeRegisterProtectCallback(
        JNIEnv* env, jobject, jobject protector) {
    pthread_mutex_lock(&g_lock);
    if (g_protobj) { env->DeleteGlobalRef(g_protobj); g_protobj = nullptr; }
    g_protobj = env->NewGlobalRef(protector);
    jclass cls = env->GetObjectClass(protector);
    g_protmid  = env->GetMethodID(cls, "protectFd", "(I)Z");
    pthread_mutex_unlock(&g_lock);
    if (!g_protmid) { LOGE("protectFd(I)Z not found"); return -1; }
    LOGI("SocketProtector registered — real VpnService.protect() active");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeUnregisterProtectCallback(
        JNIEnv* env, jobject) {
    pthread_mutex_lock(&g_lock);
    if (g_protobj) { env->DeleteGlobalRef(g_protobj); g_protobj = nullptr; }
    g_protmid = nullptr;
    pthread_mutex_unlock(&g_lock);
    LOGI("SocketProtector unregistered");
}

JNIEXPORT jboolean JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeProtectFd(JNIEnv*, jobject, jint fd) {
    return real_protect((int)fd) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeSetTunFd(JNIEnv*, jobject, jint fd) {
    if (!soren_fd_valid(fd)) { LOGE("setTunFd: bad fd=%d", fd); return -1; }
    soren_set_nonblock(fd);
    pthread_mutex_lock(&g_lock);
    g_tun_fd = fd; g_running = 1;
    pthread_mutex_unlock(&g_lock);
    LOGI("TUN fd=%d set", fd);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeSetSocketMark(
        JNIEnv*, jobject, jint fd, jint mark) {
    if (!soren_fd_valid(fd)) return -1;
    int m = (int)mark;
    if (setsockopt(fd, SOL_SOCKET, SO_MARK, &m, sizeof(m)) < 0) {
        LOGE("SO_MARK fd=%d: %s", fd, strerror(errno)); return -1;
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeCloseFd(JNIEnv*, jobject, jint fd) {
    if (fd >= 0 && soren_fd_valid(fd)) { close(fd); LOGD("closed fd=%d", fd); }
}

JNIEXPORT jstring JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeGetVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF("SorenJNI/3.0.0-real-protect");
}

JNIEXPORT jboolean JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeIsRunning(JNIEnv*, jobject) {
    return g_running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeCreateSocketPair(
        JNIEnv* env, jobject, jintArray fds) {
    int pair[2];
    if (socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, pair) < 0) {
        LOGE("socketpair: %s", strerror(errno)); return -1;
    }
    jint buf[2] = {pair[0], pair[1]};
    env->SetIntArrayRegion(fds, 0, 2, buf);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_soreng_tunnel_vpn_SorenJniBridge_nativeCleanup(JNIEnv* env, jobject) {
    pthread_mutex_lock(&g_lock);
    g_running = 0;
    if (g_tun_fd >= 0) { close(g_tun_fd); g_tun_fd = -1; LOGD("tun_fd closed"); }
    if (g_protobj) { env->DeleteGlobalRef(g_protobj); g_protobj = nullptr; }
    g_protmid = nullptr;
    pthread_mutex_unlock(&g_lock);
    LOGI("JNI cleanup complete");
}

} // extern "C"
