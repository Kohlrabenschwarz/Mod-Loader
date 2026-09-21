package dev.modloader.engine;
oneway interface IProgress {
    void update(String phase, long done, long total);
}
