#ifndef MOAR_SYSCALL_SUPPORT_H
#define MOAR_SYSCALL_SUPPORT_H

#include <sys/syscall.h>
#include <sys/types.h>
#include <linux/signal.h>
#include <unistd.h>

static inline int sys_rt_tgsigqueueinfo(pid_t pid, pid_t tid, int signo, siginfo_t *uinfo) {
    return syscall(__NR_rt_tgsigqueueinfo, pid, tid, signo, uinfo);
}

#endif