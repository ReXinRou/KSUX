#include <jni.h>

#include <sys/prctl.h>
#include <linux/capability.h>
#include <pwd.h>
#include <unistd.h>
#include <sys/wait.h>

#include <android/log.h>
#include <cerrno>
#include <cstring>
#include <string>
#include <vector>

#include "ksu.h"
#include "logging.h"

// Allowlist export/import file format. Mirrors the on-disk format written by
// the kernel: a magic + version header followed by raw app_profile records.
#define ALLOWLIST_FILE_MAGIC 0x7f4b5355
#define ALLOWLIST_FILE_HEADER_SIZE 8
#define ALLOWLIST_MIN_VERSION 2
// sizeof(struct app_profile) before the version-4 flags field was appended.
#define APP_PROFILE_SIZE_PRE_V4 776

enum allowlist_restore_result {
    ALLOWLIST_RESTORE_SUCCESS = 0,
    ALLOWLIST_RESTORE_INVALID_FILE = 1,
    ALLOWLIST_RESTORE_UNSUPPORTED_VERSION = 2,
    ALLOWLIST_RESTORE_IO_ERROR = 3,
    ALLOWLIST_RESTORE_PROFILE_ERROR = 4,
};

enum exact_read_result {
    EXACT_READ_ERROR = -2,
    EXACT_READ_PARTIAL = -1,
    EXACT_READ_EOF = 0,
    EXACT_READ_COMPLETE = 1,
};

static uint32_t read_le32(const unsigned char *data) {
    return (uint32_t) data[0] |
           ((uint32_t) data[1] << 8) |
           ((uint32_t) data[2] << 16) |
           ((uint32_t) data[3] << 24);
}

static int read_exact(int fd, void *buffer, size_t length) {
    size_t offset = 0;

    while (offset < length) {
        ssize_t count = read(fd, (char *) buffer + offset, length - offset);
        if (count > 0) {
            offset += (size_t) count;
            continue;
        }
        if (count == 0) {
            return offset == 0 ? EXACT_READ_EOF : EXACT_READ_PARTIAL;
        }
        if (errno != EINTR) {
            return EXACT_READ_ERROR;
        }
    }

    return EXACT_READ_COMPLETE;
}

static bool serialized_bool_valid(const bool *value) {
    return *(const unsigned char *) value <= 1;
}

// Bring a profile written by an older format up to the current schema.
static void migrate_allowlist_profile(uint32_t version, struct app_profile *profile) {
    if (version == 2 && profile->allow_su &&
        strncmp(profile->rp_config.profile.selinux_domain, "u:r:su:s0",
                sizeof(profile->rp_config.profile.selinux_domain)) == 0) {
        memset(profile->rp_config.profile.selinux_domain, 0,
               sizeof(profile->rp_config.profile.selinux_domain));
        strncpy(profile->rp_config.profile.selinux_domain, "u:r:ksu:s0",
                sizeof(profile->rp_config.profile.selinux_domain) - 1);
    }

    if (version < KSU_APP_PROFILE_VER && profile->allow_su) {
        profile->rp_config.profile.flags = FLAG_KSU_NO_NEW_PRIVS;
    }
    profile->version = KSU_APP_PROFILE_VER;
}

// Reject records that would be unsafe to hand to the kernel.
static bool allowlist_profile_valid(const struct app_profile *profile) {
    if (!serialized_bool_valid(&profile->allow_su) ||
        memchr(profile->key, '\0', sizeof(profile->key)) == NULL) {
        return false;
    }

    if (profile->allow_su) {
        const struct root_profile *root = &profile->rp_config.profile;
        return serialized_bool_valid(&profile->rp_config.use_default) &&
               memchr(profile->rp_config.template_name, '\0',
                      sizeof(profile->rp_config.template_name)) != NULL &&
               root->groups_count <= KSU_MAX_GROUPS &&
               root->selinux_domain[0] != '\0' &&
               memchr(root->selinux_domain, '\0', sizeof(root->selinux_domain)) != NULL;
    }

    return serialized_bool_valid(&profile->nrp_config.use_default) &&
           serialized_bool_valid(&profile->nrp_config.profile.umount_modules);
}

extern "C"
JNIEXPORT jint JNICALL
Java_me_weishu_kernelsu_Natives_getVersion(JNIEnv *env, jobject) {
    int version = get_version();
    if (version > 0) {
        return version;
    }
    // try legacy method as fallback
    return legacy_get_info().first;
}

extern "C"
JNIEXPORT jint JNICALL
Java_me_weishu_kernelsu_Natives_getKernelUAPIVersion(JNIEnv *env, jobject) {
    return get_kernel_uapi_version();
}

extern "C"
JNIEXPORT jint JNICALL
Java_me_weishu_kernelsu_Natives_getManagerUAPIVersion(JNIEnv *env, jobject) {
    return get_manager_uapi_version();
}

extern "C"
JNIEXPORT jint JNICALL
Java_me_weishu_kernelsu_Natives_getSuperuserCount(JNIEnv *env, jobject) {
    struct ksu_new_get_allow_list_cmd cmd = {
        .count = 0
    };
    bool result = get_allow_list(&cmd);
    return result ? cmd.total_count : 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isSafeMode(JNIEnv *env, jclass clazz) {
    return is_safe_mode();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isLkmMode(JNIEnv *env, jclass clazz) {
    return is_lkm_mode();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isLkmBundled(JNIEnv *env, jclass clazz) {
    return is_lkm_bundled();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_me_weishu_kernelsu_Natives_getLkmVariant(JNIEnv *env, jclass clazz) {
    const auto variant = get_lkm_variant();
    return variant.empty() ? nullptr : env->NewStringUTF(variant.c_str());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isLateLoadMode(JNIEnv *env, jclass clazz) {
    return is_late_load_mode();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isManager(JNIEnv *env, jclass clazz) {
    return is_manager();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isPrBuild(JNIEnv *env, jclass clazz) {
    return is_pr_build();
}

static void fillIntArray(JNIEnv *env, jobject list, int *data, int count) {
    auto cls = env->GetObjectClass(list);
    auto add = env->GetMethodID(cls, "add", "(Ljava/lang/Object;)Z");
    auto integerCls = env->FindClass("java/lang/Integer");
    auto constructor = env->GetMethodID(integerCls, "<init>", "(I)V");
    for (int i = 0; i < count; ++i) {
        auto integer = env->NewObject(integerCls, constructor, data[i]);
        env->CallBooleanMethod(list, add, integer);
    }
}

static void addIntToList(JNIEnv *env, jobject list, int ele) {
    auto cls = env->GetObjectClass(list);
    auto add = env->GetMethodID(cls, "add", "(Ljava/lang/Object;)Z");
    auto integerCls = env->FindClass("java/lang/Integer");
    auto constructor = env->GetMethodID(integerCls, "<init>", "(I)V");
    auto integer = env->NewObject(integerCls, constructor, ele);
    env->CallBooleanMethod(list, add, integer);
}

static uint64_t capListToBits(JNIEnv *env, jobject list) {
    auto cls = env->GetObjectClass(list);
    auto get = env->GetMethodID(cls, "get", "(I)Ljava/lang/Object;");
    auto size = env->GetMethodID(cls, "size", "()I");
    auto listSize = env->CallIntMethod(list, size);
    auto integerCls = env->FindClass("java/lang/Integer");
    auto intValue = env->GetMethodID(integerCls, "intValue", "()I");
    uint64_t result = 0;
    for (int i = 0; i < listSize; ++i) {
        auto integer = env->CallObjectMethod(list, get, i);
        int data = env->CallIntMethod(integer, intValue);

        if (cap_valid(data)) {
            result |= (1ULL << data);
        }
    }

    return result;
}

static int getListSize(JNIEnv *env, jobject list) {
    auto cls = env->GetObjectClass(list);
    auto size = env->GetMethodID(cls, "size", "()I");
    return env->CallIntMethod(list, size);
}

static void fillArrayWithList(JNIEnv *env, jobject list, int *data, int count) {
    auto cls = env->GetObjectClass(list);
    auto get = env->GetMethodID(cls, "get", "(I)Ljava/lang/Object;");
    auto integerCls = env->FindClass("java/lang/Integer");
    auto intValue = env->GetMethodID(integerCls, "intValue", "()I");
    for (int i = 0; i < count; ++i) {
        auto integer = env->CallObjectMethod(list, get, i);
        data[i] = env->CallIntMethod(integer, intValue);
    }
}

extern "C"
JNIEXPORT jobject JNICALL
Java_me_weishu_kernelsu_Natives_getAppProfile(JNIEnv *env, jobject, jstring pkg, jint uid) {
    if (env->GetStringLength(pkg) > KSU_MAX_PACKAGE_NAME) {
        return nullptr;
    }

    p_key_t key = {};
    auto cpkg = env->GetStringUTFChars(pkg, nullptr);
    strcpy(key, cpkg);
    env->ReleaseStringUTFChars(pkg, cpkg);

    app_profile profile = {};
    profile.version = KSU_APP_PROFILE_VER;

    strcpy(profile.key, key);
    profile.curr_uid = uid;

    bool useDefaultProfile = get_app_profile(&profile) != 0;

    auto cls = env->FindClass("me/weishu/kernelsu/Natives$Profile");
    auto constructor = env->GetMethodID(cls, "<init>", "()V");
    auto obj = env->NewObject(cls, constructor);
    auto keyField = env->GetFieldID(cls, "name", "Ljava/lang/String;");
    auto currentUidField = env->GetFieldID(cls, "currentUid", "I");
    auto allowSuField = env->GetFieldID(cls, "allowSu", "Z");

    auto rootUseDefaultField = env->GetFieldID(cls, "rootUseDefault", "Z");
    auto rootTemplateField = env->GetFieldID(cls, "rootTemplate", "Ljava/lang/String;");

    auto uidField = env->GetFieldID(cls, "uid", "I");
    auto gidField = env->GetFieldID(cls, "gid", "I");
    auto groupsField = env->GetFieldID(cls, "groups", "Ljava/util/List;");
    auto capabilitiesField = env->GetFieldID(cls, "capabilities", "Ljava/util/List;");
    auto domainField = env->GetFieldID(cls, "context", "Ljava/lang/String;");
    auto namespacesField = env->GetFieldID(cls, "namespace", "I");
    jfieldID flagsField = env->GetFieldID(cls, "flags", "J");

    auto nonRootUseDefaultField = env->GetFieldID(cls, "nonRootUseDefault", "Z");
    auto umountModulesField = env->GetFieldID(cls, "umountModules", "Z");

    env->SetObjectField(obj, keyField, env->NewStringUTF(profile.key));
    env->SetIntField(obj, currentUidField, profile.curr_uid);

    if (useDefaultProfile) {
        // no profile found, so just use default profile:
        // don't allow root and use default profile!
        LOGD("use default profile for: %s, %d", key, uid);

        // allow_su = false
        // non root use default = true
        env->SetBooleanField(obj, allowSuField, false);
        env->SetBooleanField(obj, nonRootUseDefaultField, true);

        return obj;
    }

    auto allowSu = profile.allow_su;

    if (allowSu) {
        env->SetBooleanField(obj, rootUseDefaultField, (jboolean) profile.rp_config.use_default);
        if (strlen(profile.rp_config.template_name) > 0) {
            env->SetObjectField(obj, rootTemplateField,
                    env->NewStringUTF(profile.rp_config.template_name));
        }

        env->SetIntField(obj, uidField, profile.rp_config.profile.uid);
        env->SetIntField(obj, gidField, profile.rp_config.profile.gid);

        jobject groupList = env->GetObjectField(obj, groupsField);
        int groupCount = profile.rp_config.profile.groups_count;
        if (groupCount > KSU_MAX_GROUPS) {
            LOGD("kernel group count too large: %d???", groupCount);
            groupCount = KSU_MAX_GROUPS;
        }
        fillIntArray(env, groupList, profile.rp_config.profile.groups, groupCount);

        jobject capList = env->GetObjectField(obj, capabilitiesField);
        for (int i = 0; i <= CAP_LAST_CAP; i++) {
            if (profile.rp_config.profile.capabilities.effective & (1ULL << i)) {
                addIntToList(env, capList, i);
            }
        }

        env->SetObjectField(obj, domainField,
                env->NewStringUTF(profile.rp_config.profile.selinux_domain));
        env->SetIntField(obj, namespacesField, profile.rp_config.profile.namespaces);
        env->SetBooleanField(obj, allowSuField, profile.allow_su);
        env->SetLongField(obj, flagsField, (jlong) profile.rp_config.profile.flags);
    } else {
        env->SetBooleanField(obj, nonRootUseDefaultField,
                (jboolean) profile.nrp_config.use_default);
        env->SetBooleanField(obj, umountModulesField, profile.nrp_config.profile.umount_modules);
    }

    return obj;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_setAppProfile(JNIEnv *env, jobject clazz, jobject profile) {
    auto cls = env->FindClass("me/weishu/kernelsu/Natives$Profile");

    auto keyField = env->GetFieldID(cls, "name", "Ljava/lang/String;");
    auto currentUidField = env->GetFieldID(cls, "currentUid", "I");
    auto allowSuField = env->GetFieldID(cls, "allowSu", "Z");

    auto rootUseDefaultField = env->GetFieldID(cls, "rootUseDefault", "Z");
    auto rootTemplateField = env->GetFieldID(cls, "rootTemplate", "Ljava/lang/String;");

    auto uidField = env->GetFieldID(cls, "uid", "I");
    auto gidField = env->GetFieldID(cls, "gid", "I");
    auto groupsField = env->GetFieldID(cls, "groups", "Ljava/util/List;");
    auto capabilitiesField = env->GetFieldID(cls, "capabilities", "Ljava/util/List;");
    auto domainField = env->GetFieldID(cls, "context", "Ljava/lang/String;");
    auto namespacesField = env->GetFieldID(cls, "namespace", "I");
    jfieldID flagsField = env->GetFieldID(cls, "flags", "J");

    auto nonRootUseDefaultField = env->GetFieldID(cls, "nonRootUseDefault", "Z");
    auto umountModulesField = env->GetFieldID(cls, "umountModules", "Z");

    auto key = env->GetObjectField(profile, keyField);
    if (!key) {
        return false;
    }
    if (env->GetStringLength((jstring) key) > KSU_MAX_PACKAGE_NAME) {
        return false;
    }

    auto cpkg = env->GetStringUTFChars((jstring) key, nullptr);
    p_key_t p_key = {};
    strcpy(p_key, cpkg);
    env->ReleaseStringUTFChars((jstring) key, cpkg);

    auto currentUid = env->GetIntField(profile, currentUidField);

    auto uid = env->GetIntField(profile, uidField);
    auto gid = env->GetIntField(profile, gidField);
    auto groups = env->GetObjectField(profile, groupsField);
    auto capabilities = env->GetObjectField(profile, capabilitiesField);
    auto domain = env->GetObjectField(profile, domainField);
    auto allowSu = env->GetBooleanField(profile, allowSuField);
    auto umountModules = env->GetBooleanField(profile, umountModulesField);

    app_profile p = {};
    p.version = KSU_APP_PROFILE_VER;

    strcpy(p.key, p_key);
    p.allow_su = allowSu;
    p.curr_uid = currentUid;

    if (allowSu) {
        p.rp_config.use_default = env->GetBooleanField(profile, rootUseDefaultField);
        auto templateName = env->GetObjectField(profile, rootTemplateField);
        if (templateName) {
            auto ctemplateName = env->GetStringUTFChars((jstring) templateName, nullptr);
            strcpy(p.rp_config.template_name, ctemplateName);
            env->ReleaseStringUTFChars((jstring) templateName, ctemplateName);
        }

        p.rp_config.profile.uid = uid;
        p.rp_config.profile.gid = gid;

        int groups_count = getListSize(env, groups);
        if (groups_count > KSU_MAX_GROUPS) {
            LOGD("groups count too large: %d", groups_count);
            return false;
        }
        p.rp_config.profile.groups_count = groups_count;
        fillArrayWithList(env, groups, p.rp_config.profile.groups, groups_count);

        p.rp_config.profile.capabilities.effective = capListToBits(env, capabilities);

        auto cdomain = env->GetStringUTFChars((jstring) domain, nullptr);
        strcpy(p.rp_config.profile.selinux_domain, cdomain);
        env->ReleaseStringUTFChars((jstring) domain, cdomain);

        p.rp_config.profile.namespaces = env->GetIntField(profile, namespacesField);

        p.rp_config.profile.flags = env->GetLongField(profile, flagsField);
    } else {
        p.nrp_config.use_default = env->GetBooleanField(profile, nonRootUseDefaultField);
        p.nrp_config.profile.umount_modules = umountModules;
    }

    return set_app_profile(&p);
}
extern "C"
JNIEXPORT jint JNICALL
Java_me_weishu_kernelsu_Natives_restoreAllowlistFromFd(JNIEnv *env, jobject, jint fd,
                                                       jintArray failedUid) {
    unsigned char header[ALLOWLIST_FILE_HEADER_SIZE];
    int result = ALLOWLIST_RESTORE_INVALID_FILE;

    int read_result = read_exact(fd, header, sizeof(header));
    if (read_result == EXACT_READ_ERROR) {
        return ALLOWLIST_RESTORE_IO_ERROR;
    }
    if (read_result != EXACT_READ_COMPLETE || read_le32(header) != ALLOWLIST_FILE_MAGIC) {
        return ALLOWLIST_RESTORE_INVALID_FILE;
    }

    uint32_t version = read_le32(header + sizeof(uint32_t));
    if (version < ALLOWLIST_MIN_VERSION || version > KSU_APP_PROFILE_VER) {
        return ALLOWLIST_RESTORE_UNSUPPORTED_VERSION;
    }
    size_t profile_size = version < KSU_APP_PROFILE_VER
                          ? APP_PROFILE_SIZE_PRE_V4
                          : sizeof(struct app_profile);

    // Parse and validate the whole file before touching any live profile so a
    // corrupt import cannot leave the allowlist half-applied.
    std::vector<struct app_profile> profiles;
    while (true) {
        struct app_profile profile = {};
        read_result = read_exact(fd, &profile, profile_size);
        if (read_result == EXACT_READ_EOF) {
            break;
        }
        if (read_result == EXACT_READ_ERROR) {
            return ALLOWLIST_RESTORE_IO_ERROR;
        }
        if (read_result != EXACT_READ_COMPLETE) {
            return ALLOWLIST_RESTORE_INVALID_FILE;
        }

        if (!serialized_bool_valid(&profile.allow_su)) {
            return ALLOWLIST_RESTORE_INVALID_FILE;
        }
        migrate_allowlist_profile(version, &profile);
        if (!allowlist_profile_valid(&profile)) {
            return ALLOWLIST_RESTORE_INVALID_FILE;
        }
        profiles.push_back(profile);
    }

    for (const struct app_profile &profile : profiles) {
        if (!set_app_profile(&profile)) {
            if (failedUid && env->GetArrayLength(failedUid) > 0) {
                jint uid = (jint) profile.curr_uid;
                env->SetIntArrayRegion(failedUid, 0, 1, &uid);
            }
            return ALLOWLIST_RESTORE_PROFILE_ERROR;
        }
    }

    result = ALLOWLIST_RESTORE_SUCCESS;
    return result;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_uidShouldUmount(JNIEnv *env, jobject thiz, jint uid) {
    return uid_should_umount(uid);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isSuEnabled(JNIEnv *env, jobject thiz) {
    return is_su_enabled();
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_setSuEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    return set_su_enabled(enabled);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isKernelUmountEnabled(JNIEnv *env, jobject thiz) {
    return is_kernel_umount_enabled();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_setKernelUmountEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    return set_kernel_umount_enabled(enabled);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isSelinuxHideEnabled(JNIEnv *env, jobject thiz) {
    return is_selinux_hide_enabled();
}

extern "C"
JNIEXPORT jint JNICALL
Java_me_weishu_kernelsu_Natives_setSelinuxHideEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    return set_selinux_hide_enabled(enabled);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_isAvcSpoofEnabled(JNIEnv *env, jobject thiz) {
    return is_avc_spoof_enabled();
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_me_weishu_kernelsu_Natives_setAvcSpoofEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    return set_avc_spoof_enabled(enabled);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_me_weishu_kernelsu_Natives_getUserName(JNIEnv *env, jobject thiz, jint uid) {
    struct passwd *pw = getpwuid((uid_t) uid);
    if (pw && pw->pw_name && pw->pw_name[0] != '\0') {
        return env->NewStringUTF(pw->pw_name);
    }
    return nullptr;
}

int fork_dont_care_and_exec_ksud(const char *path, const char *pkg) {
    int pid = fork();
    if (pid < 0) {
        PLOGE("fork");
        return pid;
    } else if (pid > 0) {
        int status = 0;
        if (TEMP_FAILURE_RETRY(waitpid(pid, &status, 0)) < 0) {
            PLOGE("waitpid");
            return -1;
        }
        if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
            LOGE("magica bootstrap child failed, status=%d", status);
        }
        return pid;
    }

    if (setuid(0) != 0) {
        PLOGE("setuid");
        _exit(1);
    }

    pid = fork();
    if (pid < 0) {
        PLOGE("fork 2");
        _exit(1);
    } else if (pid > 0) {
        _exit(0);
    }

    execl(path, "ksud", "late-load", "--magica", "5555", "--package-name", pkg, nullptr);
    PLOGE("exec magica");
    _exit(1);
}

extern "C"
JNIEXPORT void JNICALL
Java_me_weishu_kernelsu_magica_AppZygotePreload_forkDontCareAndExecKsud(JNIEnv *env, jclass clazz,
                                                                        jstring ksud_path, jstring pkg_name) {
    auto path = env->GetStringUTFChars(ksud_path, nullptr);
    auto pkg = env->GetStringUTFChars(pkg_name, nullptr);
    LOGD("executing magica %s (pkg %s)", path, pkg);
    fork_dont_care_and_exec_ksud(path, pkg);
    env->ReleaseStringUTFChars(ksud_path, path);
    env->ReleaseStringUTFChars(pkg_name, pkg);
}
