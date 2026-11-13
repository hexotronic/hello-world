// Host test of the LAME build: encodes 5 s of a stereo test tone the way the app does
// (stereo in, mono speech preset out) and writes it to the file given as argument.
#include <math.h>
#include <stdio.h>
#include <lame.h>

int main(int argc, char **argv) {
    const int rate = 44100, seconds = 5;
    lame_global_flags *gf = lame_init();
    lame_set_in_samplerate(gf, rate);
    lame_set_num_channels(gf, 2);
    lame_set_out_samplerate(gf, 22050);
    lame_set_VBR(gf, vbr_off);
    lame_set_brate(gf, 64);
    lame_set_mode(gf, MONO);
    lame_set_quality(gf, 5);
    lame_set_bWriteVbrTag(gf, 0);
    lame_set_write_id3tag_automatic(gf, 0);
    if (lame_init_params(gf) < 0) return 1;
    FILE *f = fopen(argc > 1 ? argv[1] : "test.mp3", "wb");
    static short pcm[4410 * 2];
    static unsigned char buf[16384];
    for (int block = 0; block < seconds * 10; block++) {
        for (int i = 0; i < 4410; i++) {
            double t = (block * 4410 + i) / (double) rate;
            short v = (short) (8000 * sin(2 * M_PI * 440 * t));
            pcm[2 * i] = v;
            pcm[2 * i + 1] = v;
        }
        int n = lame_encode_buffer_interleaved(gf, pcm, 4410, buf, sizeof(buf));
        fwrite(buf, 1, n, f);
    }
    int n = lame_encode_flush(gf, buf, sizeof(buf));
    fwrite(buf, 1, n, f);
    fclose(f);
    lame_close(gf);
    printf("LAME %s ok\n", get_lame_version());
    return 0;
}
