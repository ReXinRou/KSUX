#ifndef __KSU_H_MODULE_LOAD_FILTER
#define __KSU_H_MODULE_LOAD_FILTER

#include <linux/types.h>

// Comma-separated list of preset kernel module names to acknowledge without
// loading, set via the `block_modules` module parameter.
#define KSU_BLOCK_MODULES_MAX_LEN 256
extern char ksu_block_modules[KSU_BLOCK_MODULES_MAX_LEN];

// Return 0 when the module must be acknowledged without loading (the caller
// returns 0 to userspace), otherwise 1 so the caller proceeds with the real
// syscall.
int ksu_handle_init_module(const void __user *umod, unsigned long umod_len);
int ksu_handle_finit_module(int fd, int flags);

void ksu_module_load_filter_hook_init(void);
void ksu_module_load_filter_hook_exit(void);

#endif
