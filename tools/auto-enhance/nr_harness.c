/*
 * Runs the original HTC/Scalado noise reduction from libcameraservice.so
 * on a synthetic YVU420SP frame, on the device itself.
 *
 * Build (NDK r10e, API 10):
 *   arm-linux-androideabi-gcc --sysroot=$NDK/platforms/android-10/arch-arm \
 *     -O2 -o nr_harness nr_harness.c -ldl
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <dlfcn.h>

typedef int (*create_img_t)(uint32_t, uint32_t, uint32_t, void **);
typedef int (*create_t)(void **);
typedef int (*set_level_t)(void *, uint32_t);
typedef void (*process_t)(void *, void *);
typedef int (*to_buf_t)(void *, void *);
typedef void (*destroy_img_t)(void *);
typedef void (*destroy_t)(void *);

int main(int argc, char **argv) {
    int W = 256, H = 256;
    const char *outPath = "/data/local/tmp/nr_out.yuv";
    int level = 5;
    int pattern = 0;
    if (argc > 1) W = atoi(argv[1]);
    if (argc > 2) H = atoi(argv[2]);
    if (argc > 3) outPath = argv[3];
    if (argc > 4) level = atoi(argv[4]);
    if (argc > 5) pattern = atoi(argv[5]);

    void *lib = dlopen("/system/lib/libcameraservice.so", RTLD_NOW);
    if (!lib) {
        fprintf(stderr, "dlopen failed: %s\n", dlerror());
        return 1;
    }
    create_img_t cimg = (create_img_t)dlsym(lib, "ctnoisereduction_createNRImageFromYVU420SPBuffer");
    create_t create = (create_t)dlsym(lib, "ctnoisereduction_create");
    set_level_t setLevel = (set_level_t)dlsym(lib, "ctnoisereduction_setLevel");
    process_t process = (process_t)dlsym(lib, "ctnoisereduction_processImage");
    to_buf_t toBuf = (to_buf_t)dlsym(lib, "ctnoisereduction_setYVU420SPBufferFromNRImage");
    destroy_img_t destroyImg = (destroy_img_t)dlsym(lib, "ctnoisereduction_destroyNRImage");
    destroy_t destroy = (destroy_t)dlsym(lib, "ctnoisereduction_destroy");
    if (!cimg || !create || !setLevel || !process || !toBuf) {
        fprintf(stderr, "dlsym failed\n");
        return 2;
    }

    size_t ySize = (size_t)W * H;
    size_t uvSize = ySize / 2;
    unsigned char *in = (unsigned char *)malloc(ySize + uvSize);
    unsigned char *out = (unsigned char *)malloc(ySize + uvSize);
    if (!in || !out) return 3;

    /* Deterministic pattern: gradient + pseudo-random noise + edge block. */
    uint32_t rnd = 123456789u;
    for (size_t i = 0; i < ySize; i++) {
        int x = (int)(i % W), y = (int)(i / W);
        int v;
        if (pattern == 1) {
            v = 128;
            if (x == 32 && y == 32) v = 255;
        } else if (pattern == 2) {
            v = (x < W / 2) ? 100 : 160;
        } else if (pattern == 4) {
            v = 128;
            if (x == 32 && y == 32) v = 0;
        } else if (pattern == 5) {
            v = (y < H / 2) ? 100 : 160;
        } else if (pattern == 3) {
            rnd = rnd * 1103515245u + 12345u;
            v = 128 + (int)((rnd >> 16) & 0x3F) - 32;
        } else {
            rnd = rnd * 1103515245u + 12345u;
            int noise = (int)((rnd >> 16) & 0x1F) - 16;
            int base = (x * 255) / (W - 1);
            if (x > W / 2 && y > H / 2) base = 200;
            v = base + noise;
        }
        if (v < 0) v = 0;
        if (v > 255) v = 255;
        in[i] = (unsigned char)v;
    }
    for (size_t i = 0; i < uvSize; i++) {
        in[ySize + i] = 128;
    }
    memcpy(out, in, ySize + uvSize);
    {
        char inPath[512];
        snprintf(inPath, sizeof(inPath), "%s.in", outPath);
        FILE *fi = fopen(inPath, "wb");
        if (fi) { fwrite(in, 1, ySize + uvSize, fi); fclose(fi); }
    }

    void *img = NULL;
    int rc = cimg((uint32_t)(uintptr_t)in, (uint32_t)W, (uint32_t)H, &img);
    fprintf(stderr, "createNRImage rc=%d img=%p\n", rc, img);
    if (rc != -0xff && rc != 0) { }

    void *nr = NULL;
    rc = create(&nr);
    fprintf(stderr, "create rc=%d nr=%p\n", rc, nr);

    rc = setLevel(nr, (uint32_t)level);
    fprintf(stderr, "setLevel(%d) rc=%d\n", level, rc);

    process(nr, img);

    rc = toBuf(img, out);
    fprintf(stderr, "toBuf rc=%d\n", rc);

    unsigned long sumIn = 0, sumOut = 0;
    for (size_t i = 0; i < ySize; i++) { sumIn += in[i]; sumOut += out[i]; }
    fprintf(stderr, "Y checksums in=%lu out=%lu\n", sumIn, sumOut);

    FILE *f = fopen(outPath, "wb");
    if (!f) { perror("fopen"); return 4; }
    fwrite(in, 1, ySize + uvSize, f);  /* library writes back into the wrapped buffer */
    fclose(f);

    if (destroy) destroy(nr);
    if (destroyImg) destroyImg(img);
    fprintf(stderr, "wrote %s (%dx%d)\n", outPath, W, H);
    return 0;
}
