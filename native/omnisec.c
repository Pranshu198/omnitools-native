/*
 * libomnisec — the small native side of OmniTools Native's anti-copy
 * checks. It holds the two expected values that must not sit in the dex
 * as readable strings (the release signer's certificate digest and the
 * sealed dex/resources hash), XOR-obfuscated, and performs the
 * comparisons. It also decodes the obfuscated endpoint strings.
 *
 * Honest strength: this raises the bar against casual repacking and
 * string-grepping. It is not a root of trust — a determined attacker can
 * patch this library or its Java caller. It exists so that the naive
 * attacks (re-sign with a new key, edit the dex, grep the endpoints)
 * fail, and so the values are not gift-wrapped.
 */
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

static const unsigned char K[4] = {0x3C, 0x5A, 0x71, 0x26};

static void deobf(const unsigned char *in, size_t n, char *out) {
    for (size_t i = 0; i < n; i++) out[i] = (char)(in[i] ^ K[i % 4]);
    out[n] = '\0';
}

/* Release signer certificate SHA-256 (hex), XOR-obfuscated. */
static const unsigned char SIG_EXPECTED[] = {
    0x5a, 0x62, 0x43, 0x1f, 0x5f, 0x6b, 0x41, 0x47, 0x0c, 0x6e, 0x17, 0x10,
    0x0c, 0x6a, 0x41, 0x12, 0x0d, 0x6a, 0x40, 0x44, 0x0a, 0x6f, 0x15, 0x17,
    0x59, 0x3c, 0x47, 0x15, 0x09, 0x3f, 0x40, 0x42, 0x5d, 0x39, 0x13, 0x11,
    0x0e, 0x3e, 0x49, 0x47, 0x5d, 0x63, 0x48, 0x12, 0x0c, 0x62, 0x47, 0x1f,
    0x5a, 0x68, 0x14, 0x42, 0x0e, 0x38, 0x10, 0x40, 0x08, 0x6d, 0x49, 0x47,
    0x0d, 0x6d, 0x47, 0x12
};

/* Sealed classes*.dex + resources.arsc SHA-256 (hex), XOR-obfuscated.
 * Generated per build by gen_hashes.py (two-pass build, see
 * build-native.sh). All-zero bytes mean "unsealed" and nDex fails OPEN
 * (returns true) rather than locking a build that was never sealed. */
#include "sec_hashes.h"

static int equals_expected(const unsigned char *obf, size_t n, const char *actual) {
    if (actual == NULL) return 0;
    char *want = (char *)malloc(n + 1);
    if (!want) return 0;
    deobf(obf, n, want);
    size_t alen = strlen(actual);
    int eq = (alen == n);
    if (eq) {
        for (size_t i = 0; i < n; i++) {
            if (tolower((unsigned char)actual[i]) != tolower((unsigned char)want[i])) { eq = 0; break; }
        }
    }
    /* scrub the plaintext copy */
    memset(want, 0, n + 1);
    free(want);
    return eq;
}

static int all_zero(const unsigned char *p, size_t n) {
    for (size_t i = 0; i < n; i++) if (p[i] != 0) return 0;
    return 1;
}

/* ------------------------------------------------------------ nSig */
JNIEXPORT jboolean JNICALL
Java_com_jackbhai_omnitools_nativ_Sec_nSig(JNIEnv *env, jclass clazz, jstring actual) {
    (void)clazz;
    const char *a = (*env)->GetStringUTFChars(env, actual, NULL);
    if (!a) return JNI_TRUE; /* unreadable: fail-open, mirrors the Java policy */
    int ok = equals_expected(SIG_EXPECTED, sizeof(SIG_EXPECTED), a);
    (*env)->ReleaseStringUTFChars(env, actual, a);
    return ok ? JNI_TRUE : JNI_FALSE;
}

/* ------------------------------------------------------------ nDex */
JNIEXPORT jboolean JNICALL
Java_com_jackbhai_omnitools_nativ_Sec_nDex(JNIEnv *env, jclass clazz, jstring actual) {
    (void)clazz;
    if (all_zero(DEX_EXPECTED, sizeof(DEX_EXPECTED))) return JNI_TRUE; /* unsealed build */
    const char *a = (*env)->GetStringUTFChars(env, actual, NULL);
    if (!a) return JNI_TRUE;
    int ok = equals_expected(DEX_EXPECTED, sizeof(DEX_EXPECTED), a);
    (*env)->ReleaseStringUTFChars(env, actual, a);
    return ok ? JNI_TRUE : JNI_FALSE;
}

/* ------------------------------------------------------------ nEp */
static int b64val(int c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

JNIEXPORT jstring JNICALL
Java_com_jackbhai_omnitools_nativ_Sec_nEp(JNIEnv *env, jclass clazz, jstring encoded) {
    (void)clazz;
    const char *in = (*env)->GetStringUTFChars(env, encoded, NULL);
    if (!in) return NULL;
    size_t len = strlen(in);
    char *out = (char *)malloc(len + 1);
    if (!out) { (*env)->ReleaseStringUTFChars(env, encoded, in); return NULL; }
    size_t o = 0;
    int val = 0, valb = -8;
    for (size_t i = 0; i < len; i++) {
        int d = b64val((unsigned char)in[i]);
        if (d < 0) {
            if (in[i] == '=') break;
            continue; /* skip whitespace/newlines (MIME-style input) */
        }
        val = (val << 6) | d;
        valb += 6;
        if (valb >= 0) {
            out[o++] = (char)((val >> valb) & 0xFF);
            valb -= 8;
        }
    }
    out[o] = '\0';
    (*env)->ReleaseStringUTFChars(env, encoded, in);
    jstring r = (*env)->NewStringUTF(env, out);
    memset(out, 0, len + 1);
    free(out);
    return r;
}
