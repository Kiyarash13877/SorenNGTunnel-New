#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <android/log.h>
#define TAG "TunHelper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

int soren_fd_valid(int fd) {
    if (fd < 0) return 0;
    int r = fcntl(fd, F_GETFD);
    return !(r == -1 && errno == EBADF);
}
int soren_set_nonblock(int fd) {
    if (fd < 0) return -1;
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) { LOGE("F_GETFL fd=%d: %s", fd, strerror(errno)); return -1; }
    return fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}
