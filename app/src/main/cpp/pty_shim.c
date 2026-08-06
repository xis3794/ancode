/*
 * Ancode PTY shim — minimal pseudo-terminal bridge for the interactive terminal.
 *
 * Exposes a small set of JNI functions around posix_openpt / fork / exec so the
 * Kotlin layer can spawn a shell (e.g. proot + bash) attached to a real PTY and
 * stream bytes in/out. No root required (devpts is accessible to apps).
 */
#include <jni.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "AncodePty"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * Spawn a child process attached to a fresh PTY.
 * Returns long[]{masterFd, childPid} or null on failure.
 */
JNIEXPORT jlongArray JNICALL
Java_com_ancode_app_linux_Pty_nativeSpawn(JNIEnv *env, jclass clazz, jobjectArray cmdArray) {
    if (cmdArray == NULL || (*env)->GetArrayLength(env, cmdArray) == 0) {
        return NULL;
    }

    int master = posix_openpt(O_RDWR | O_NOCTTY);
    if (master < 0) {
        LOGE("posix_openpt failed: %s", strerror(errno));
        return NULL;
    }
    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        LOGE("grantpt/unlockpt failed: %s", strerror(errno));
        close(master);
        return NULL;
    }
    char *slave_name = ptsname(master);
    if (slave_name == NULL) {
        LOGE("ptsname failed: %s", strerror(errno));
        close(master);
        return NULL;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(master);
        return NULL;
    }

    if (pid == 0) {
        /* ---- child ---- */
        setsid();
        int slave = open(slave_name, O_RDWR);
        if (slave < 0) _exit(127);
        dup2(slave, 0);
        dup2(slave, 1);
        dup2(slave, 2);
        close(slave);
        close(master);

        int argc = (*env)->GetArrayLength(env, cmdArray);
        char **argv = calloc((size_t)(argc + 1), sizeof(char *));
        if (argv == NULL) _exit(127);
        for (int i = 0; i < argc; i++) {
            jstring js = (jstring)(*env)->GetObjectArrayElement(env, cmdArray, i);
            const char *s = (*env)->GetStringUTFChars(env, js, NULL);
            argv[i] = strdup(s != NULL ? s : "");
            (*env)->ReleaseStringUTFChars(env, js, s);
            (*env)->DeleteLocalRef(env, js);
        }
        argv[argc] = NULL;
        execv(argv[0], argv);
        LOGE("execv failed: %s", strerror(errno));
        _exit(127);
    }

    /* ---- parent ---- */
    jlongArray result = (*env)->NewLongArray(env, 2);
    if (result == NULL) {
        close(master);
        return NULL;
    }
    jlong vals[2] = {(jlong)master, (jlong)pid};
    (*env)->SetLongArrayRegion(env, result, 0, 2, vals);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_ancode_app_linux_Pty_nativeRead(JNIEnv *env, jclass clazz, jint fd, jbyteArray buf) {
    if (buf == NULL) return -1;
    jsize len = (*env)->GetArrayLength(env, buf);
    jbyte *tmp = (jbyte *)malloc((size_t)len);
    if (tmp == NULL) return -1;
    ssize_t n = read((int)fd, tmp, (size_t)len);
    if (n > 0) {
        (*env)->SetByteArrayRegion(env, buf, 0, (jsize)n, tmp);
    }
    free(tmp);
    return (jint)n;
}

JNIEXPORT jint JNICALL
Java_com_ancode_app_linux_Pty_nativeWrite(JNIEnv *env, jclass clazz, jint fd, jbyteArray data, jint off, jint len) {
    if (data == NULL) return -1;
    jbyte *tmp = (jbyte *)malloc((size_t)len);
    if (tmp == NULL) return -1;
    (*env)->GetByteArrayRegion(env, data, off, len, tmp);
    ssize_t n = write((int)fd, tmp, (size_t)len);
    free(tmp);
    return (jint)n;
}

JNIEXPORT void JNICALL
Java_com_ancode_app_linux_Pty_nativeResize(JNIEnv *env, jclass clazz, jint fd, jint rows, jint cols) {
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short)rows;
    ws.ws_col = (unsigned short)cols;
    ioctl((int)fd, TIOCSWINSZ, &ws);
}

JNIEXPORT void JNICALL
Java_com_ancode_app_linux_Pty_nativeClose(JNIEnv *env, jclass clazz, jint fd) {
    close((int)fd);
}
