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

/* ------------------------------------------------------------ nToken ---
 * Server-token for the omni-proxy worker (Track D). token =
 * HMAC-SHA256(key, "<timestampSeconds>.<signerCertSha256HexLower>"),
 * returned as lowercase hex. The key is a sealed 32-byte string, XOR-
 * obfuscated with the same K as the other sealed values; the worker
 * carries the mirror copy (also obfuscated) and recomputes the HMAC.
 * Same honest strength as the rest of this file: a bar-raiser against
 * casual abuse of the worker route, not a root of trust. */

/* Sealed HMAC key (32 bytes), XOR-obfuscated. */
static const unsigned char TOKEN_KEY_OBF[] = {
    0x9f, 0x9a, 0x12, 0x2a, 0x75, 0x53, 0xc1, 0x3c, 0x34, 0x9a, 0x74, 0xd0,
    0x11, 0xde, 0x94, 0x06, 0xdc, 0x75, 0xdf, 0xe5, 0x36, 0x08, 0x5f, 0xc9,
    0x21, 0xd5, 0x71, 0x92, 0xfb, 0xc5, 0x56, 0xcc
};

/* Minimal SHA-256 (FIPS 180-4), just enough for HMAC. */
typedef struct {
    unsigned int h[8];
    unsigned long long len;
    unsigned char buf[64];
    size_t buflen;
} sha256_ctx;

static const unsigned int SHA256_K[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1,
    0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
    0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
    0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
    0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
    0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

#define ROTR(x, n) (((x) >> (n)) | ((x) << (32 - (n))))

static void sha256_block(sha256_ctx *c, const unsigned char *p) {
    unsigned int w[64];
    for (int i = 0; i < 16; i++)
        w[i] = ((unsigned int)p[i * 4] << 24) | ((unsigned int)p[i * 4 + 1] << 16)
             | ((unsigned int)p[i * 4 + 2] << 8) | (unsigned int)p[i * 4 + 3];
    for (int i = 16; i < 64; i++) {
        unsigned int s0 = ROTR(w[i - 15], 7) ^ ROTR(w[i - 15], 18) ^ (w[i - 15] >> 3);
        unsigned int s1 = ROTR(w[i - 2], 17) ^ ROTR(w[i - 2], 19) ^ (w[i - 2] >> 10);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    unsigned int a = c->h[0], b = c->h[1], cc = c->h[2], d = c->h[3];
    unsigned int e = c->h[4], f = c->h[5], g = c->h[6], h = c->h[7];
    for (int i = 0; i < 64; i++) {
        unsigned int S1 = ROTR(e, 6) ^ ROTR(e, 11) ^ ROTR(e, 25);
        unsigned int ch = (e & f) ^ (~e & g);
        unsigned int t1 = h + S1 + ch + SHA256_K[i] + w[i];
        unsigned int S0 = ROTR(a, 2) ^ ROTR(a, 13) ^ ROTR(a, 22);
        unsigned int mj = (a & b) ^ (a & cc) ^ (b & cc);
        unsigned int t2 = S0 + mj;
        h = g; g = f; f = e; e = d + t1; d = cc; cc = b; b = a; a = t1 + t2;
    }
    c->h[0] += a; c->h[1] += b; c->h[2] += cc; c->h[3] += d;
    c->h[4] += e; c->h[5] += f; c->h[6] += g; c->h[7] += h;
}

static void sha256_init(sha256_ctx *c) {
    static const unsigned int H0[8] = {
        0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
        0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    };
    memcpy(c->h, H0, sizeof(H0));
    c->len = 0;
    c->buflen = 0;
}

static void sha256_update(sha256_ctx *c, const unsigned char *p, size_t n) {
    c->len += n;
    while (n) {
        size_t take = 64 - c->buflen;
        if (take > n) take = n;
        memcpy(c->buf + c->buflen, p, take);
        c->buflen += take;
        p += take;
        n -= take;
        if (c->buflen == 64) { sha256_block(c, c->buf); c->buflen = 0; }
    }
}

static void sha256_final(sha256_ctx *c, unsigned char out[32]) {
    unsigned long long bits = c->len * 8;
    unsigned char pad = 0x80;
    sha256_update(c, &pad, 1);
    unsigned char zero = 0;
    while (c->buflen != 56) sha256_update(c, &zero, 1);
    unsigned char lb[8];
    for (int i = 0; i < 8; i++) lb[i] = (unsigned char)(bits >> (56 - 8 * i));
    /* buflen is 56: the length block completes the final block directly,
       bypassing update() so c->len is not polluted (bits already taken). */
    memcpy(c->buf + 56, lb, 8);
    sha256_block(c, c->buf);
    for (int i = 0; i < 8; i++) {
        out[i * 4] = (unsigned char)(c->h[i] >> 24);
        out[i * 4 + 1] = (unsigned char)(c->h[i] >> 16);
        out[i * 4 + 2] = (unsigned char)(c->h[i] >> 8);
        out[i * 4 + 3] = (unsigned char)(c->h[i]);
    }
}

static void hmac_sha256(const unsigned char *key, size_t klen,
                        const unsigned char *msg, size_t mlen,
                        unsigned char out[32]) {
    unsigned char kblock[64];
    memset(kblock, 0, sizeof(kblock));
    if (klen > 64) {
        sha256_ctx c; sha256_init(&c); sha256_update(&c, key, klen);
        sha256_final(&c, kblock);
    } else {
        memcpy(kblock, key, klen);
    }
    unsigned char pad[64];
    sha256_ctx c;
    for (int i = 0; i < 64; i++) pad[i] = (unsigned char)(kblock[i] ^ 0x36);
    sha256_init(&c); sha256_update(&c, pad, 64); sha256_update(&c, msg, mlen);
    unsigned char inner[32];
    sha256_final(&c, inner);
    for (int i = 0; i < 64; i++) pad[i] = (unsigned char)(kblock[i] ^ 0x5c);
    sha256_init(&c); sha256_update(&c, pad, 64); sha256_update(&c, inner, 32);
    sha256_final(&c, out);
    memset(kblock, 0, sizeof(kblock));
    memset(pad, 0, sizeof(pad));
    memset(inner, 0, sizeof(inner));
}

JNIEXPORT jstring JNICALL
Java_com_jackbhai_omnitools_nativ_Sec_nToken(JNIEnv *env, jclass clazz,
                                             jlong timestampSeconds,
                                             jstring sigHex) {
    (void)clazz;
    if (!sigHex) return NULL;
    const char *sig = (*env)->GetStringUTFChars(env, sigHex, NULL);
    if (!sig) return NULL;
    /* message: "<ts>.<sighex-lower>" — the worker builds it identically */
    char msg[128];
    size_t slen = strlen(sig);
    if (slen > 96) { (*env)->ReleaseStringUTFChars(env, sigHex, sig); return NULL; }
    int mlen = snprintf(msg, sizeof(msg), "%lld.", (long long)timestampSeconds);
    if (mlen < 0 || (size_t)mlen + slen >= sizeof(msg)) {
        (*env)->ReleaseStringUTFChars(env, sigHex, sig);
        return NULL;
    }
    for (size_t i = 0; i < slen; i++)
        msg[mlen + i] = (char)tolower((unsigned char)sig[i]);
    mlen += (int)slen;
    msg[mlen] = '\0';
    (*env)->ReleaseStringUTFChars(env, sigHex, sig);

    /* +1: deobf also writes a NUL terminator past the key bytes */
    unsigned char key[sizeof(TOKEN_KEY_OBF) + 1];
    deobf(TOKEN_KEY_OBF, sizeof(TOKEN_KEY_OBF), (char *)key);
    unsigned char mac[32];
    hmac_sha256(key, sizeof(TOKEN_KEY_OBF), (const unsigned char *)msg, (size_t)mlen, mac);
    memset(key, 0, sizeof(key));

    static const char HEXD[] = "0123456789abcdef";
    char hex[65];
    for (int i = 0; i < 32; i++) {
        hex[i * 2] = HEXD[mac[i] >> 4];
        hex[i * 2 + 1] = HEXD[mac[i] & 0x0F];
    }
    hex[64] = '\0';
    memset(mac, 0, sizeof(mac));
    return (*env)->NewStringUTF(env, hex);
}
